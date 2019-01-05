import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.Arrays;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

public class TomasuloRunner {	
	public static final int INVALID_VALUE = -999;
	public static final int INVALID_LOC = -1;
	
	private static int NUM_EXC_IA;
	private static int NUM_RS_IA;
	private static int NUM_EXC_FPA;
	private static int NUM_RS_FPA;
	private static int NUM_EXC_FPM;
	private static int NUM_RS_FPM;
	private static int NUM_EXC_LSU;
	private static int NUM_RS_LSU;
	
	private static int NUM_MEMC_LSU;
	private static int NUM_ROB_ENT;
	private static int CDB_BUF_SIZE;
	
	private static final String[] NOOP = {"ADDI", "R0", "R0", "0"};
	private static final int DEBUG = 0;
	
	public static void main(String [] args) throws FileNotFoundException
	{
		//track current cycle
		int cycle = 0;
		
		Scanner inputFile = null;;
		PrintWriter output = null;;
		
		try
		{
			File inputF = new File("testcase.txt");
			inputFile = new Scanner(inputF);
			
			output = new PrintWriter("output.txt", "UTF-8");
		} 
		catch(FileNotFoundException e)
		{
			System.out.println("File not found!");
		}
		catch(UnsupportedEncodingException f)
		{
			System.out.println("Unsupported Encoding Type!");
		}
		
		//parse input file
			//ignore first line of input file
		inputFile.nextLine();
			//parse integer adder details
		String[] tokens = inputFile.nextLine().split(",");
		NUM_RS_IA = Character.getNumericValue((tokens[0].charAt(tokens[0].length()-1)));
		NUM_EXC_IA = Integer.parseInt(tokens[1].trim());
			//parse fp adder details
		tokens = inputFile.nextLine().split(",");
		NUM_RS_FPA = Character.getNumericValue((tokens[0].charAt(tokens[0].length()-1)));
		NUM_EXC_FPA = Integer.parseInt(tokens[1].trim());
			//parse fp mult details
		tokens = inputFile.nextLine().split(",");
		NUM_RS_FPM = Character.getNumericValue((tokens[0].charAt(tokens[0].length()-1)));
		NUM_EXC_FPM = Integer.parseInt(tokens[1].trim());
			//parse lsu details
		tokens = inputFile.nextLine().split(",");
		NUM_RS_LSU = Character.getNumericValue((tokens[0].charAt(tokens[0].length()-1)));
		NUM_EXC_LSU = Integer.parseInt(tokens[1].trim());
		NUM_MEMC_LSU = Integer.parseInt(tokens[2].trim());
			//parse ROB details
		inputFile.nextLine();
		tokens = inputFile.nextLine().split("=");
		NUM_ROB_ENT = Integer.parseInt(tokens[1].trim());
			//parse CDB buffer details
		tokens = inputFile.nextLine().split("=");
		CDB_BUF_SIZE = Integer.parseInt(tokens[1].trim());
		
		String[] instBuff = new String[20];
		int numInstructs = 0;
		
		//ARF has size 64, entries 0-31 are R registers and entries 32-63 are FP registers
		double[] ARF = new double[64];
		ARF[0] = 0;
		
		//RAT has size 64, corresponding to ARF
		String[] RAT = new String[64];
		for(int i = 0; i < 64; i++)
		{
			RAT[i] = "ARF" + i;
		}
		
		double[] memory = new double[64];
		
		//initialize ROB
		ROB robrob = new ROB(NUM_ROB_ENT);
		
		//initialize reservation stations, load/store Q, CDB and BTB
		IntRS iaRS = new IntRS(NUM_RS_IA);
		FloatingPtRS faRS = new FloatingPtRS(NUM_RS_FPA);
		FloatingPtRS fmRS = new FloatingPtRS(NUM_RS_FPM);
		LSQ lsq = new LSQ(NUM_RS_LSU);
		cdbBuffer CDB = new cdbBuffer(CDB_BUF_SIZE);
		BTB btb = new BTB();
		
		//parse intial ARF values
		tokens = inputFile.nextLine().split(",");
		String[] temp;
		for(String token : tokens)
		{
			if(DEBUG == 1)
				System.out.print(token);
			temp = token.trim().split("=");
			if(temp[0].toUpperCase().charAt(0) == 'R')
			{
				if(Integer.parseInt(temp[0].trim().substring(1)) != 0)
					ARF[Integer.parseInt(temp[0].trim().substring(1))] = Double.parseDouble(temp[1]);
			}
			else
				ARF[Integer.parseInt(temp[0].trim().substring(1))+32] = Double.parseDouble(temp[1]);
		}
		//parse initial mem values
		tokens = inputFile.nextLine().split(",");
		for(String token : tokens)
		{
			temp = token.trim().split("=");
			memory[Integer.parseInt(temp[0].trim().substring(4,temp[0].length()-1))/4] = Double.parseDouble(temp[1]);
		}
		
		//parse instructions
		inputFile.nextLine();
		while(inputFile.hasNextLine())
		{
			instBuff[numInstructs] = inputFile.nextLine().toUpperCase().replace(',',' ');
			numInstructs++;
		}
		
		//initialize Instruction Status Table
		InstrST IST = new InstrST(NUM_ROB_ENT);
		
		//initialize Branch Recovery Table
		BRT bert = new BRT(15);
		
		//RUN
		int currInstr = 0;
		int currRob = 0;
		int prediction = INVALID_LOC;
		
			//Matrix containing 3 entries:
				//cycles remaining (when 0 ready to go to cdb)
				//value
				//2 for store 1 for load 0 for ex
		double[][] cyclesRem = new double[NUM_ROB_ENT][3];
		
		for(int ind = 0; ind < NUM_ROB_ENT; ind++)
		{
			cyclesRem[ind][0] = INVALID_LOC;
			cyclesRem[ind][1] = INVALID_VALUE;
			cyclesRem[ind][2] = 0;
		}
		double[] results;
		
		int regAddr, offset;
		double arg1Val, arg2Val;
		int arg1Loc, arg2Loc;
		String[] tempTok;
		int numTokens;
		int lastIntCycleExec = -1; //Last time an int reservation station was executed
		int [] res = {INVALID_LOC};
		boolean squashed = false;
		
		//Second part of this while condition used for testing
		while(!robrob.doneExecuting())// && cycle < 20)
		{
			cycle++;
						
			numTokens = 0;
			tokens = new String[4];
			
			//add noop if at end of instructions but not everything has been committed
			if(instBuff[currInstr] == null || instBuff[currInstr].trim().length() == 0)
			{
				tokens = NOOP;
				numTokens = 4;
			}
			else
			{
				//read next instruction
				tempTok = instBuff[currInstr].split(" ");
				for(int j = 0; j < tempTok.length; j++)
				{
					if(!tempTok[j].trim().equals(""))
					{
						tokens[numTokens] = tempTok[j];
						numTokens++;
					}
				}
			}
			
			System.out.print("Current Instruction: ");
			if(tokens == NOOP)
				System.out.println("NOOP");
			else
			{
				for(String token : tokens)
					System.out.print(token + " ");
				System.out.println("");
			}
			
			regAddr = INVALID_LOC;
			arg1Val = arg2Val = INVALID_VALUE;
			arg1Loc = arg2Loc = INVALID_LOC;
			
			///-----------------------------\
			//|*****************************|
			//|*********COMMIT Stage********|
			//|*****************************|
			//\-----------------------------/
				//results[0] is robLoc, results[1] is dest, results[2] is val
			results = robrob.commit();
			if(results[0] != INVALID_LOC)
			{
				if(results[1] != INVALID_LOC)
					if(results[1] > 1000)
					{
						//if in here, then store is found
						memory[(int)(results[1]/1000) - 4] = results[2];
						lsq.commitStore((int)results[0]);
					}
					else
						ARF[(int)results[1]] = results[2];
				IST.editCommitCyc((int)results[0], cycle);
			}
			
			///-----------------------------\
			//|*****************************|
			//|********MEMORY Stage*********|
			//|*****************************|
			//\-----------------------------/
			
				//make
			results = lsq.memFetch();
			if(results[0] != INVALID_LOC)
			{
				//tell array that it's a memory access
				IST.editMemCyc((int)results[0], cycle);
				
				if(results[1] != INVALID_VALUE)
				{
					//if load found
					if(results[3] == 0)
					{
						cyclesRem[(int)results[0]][0] = 1;
						cyclesRem[(int)results[0]][1] = results[1];
						cyclesRem[(int)results[0]][2] = 1;
					}
					//if store found
					else
					{
						//stores take one less cycle since they ignore the WB stage
						cyclesRem[(int)results[0]][0] = NUM_MEMC_LSU - 1;
						cyclesRem[(int)results[0]][1] = results[1];
						//altered address trick to get proper address later on in Commit
						cyclesRem[(int)results[0]][2] = results[2] + 4;
					}
				}
				else
				{
					cyclesRem[(int)results[0]][0] = NUM_MEMC_LSU;
					cyclesRem[(int)results[0]][1] = memory[(int)(results[2])];
					cyclesRem[(int)results[0]][2] = 1;
				}
			}
			
				//check & send something to CDB
			for(int ind = 0; ind < NUM_ROB_ENT; ind++)
			{
				if(cyclesRem[ind][0] == 0 && cyclesRem[ind][2] > 0)
				{
					//check for unique case where two instructions are ready at the same time
					for(int prevInd = 0; prevInd < ind; prevInd++)
					{
						if(cyclesRem[prevInd][0] == 0 && cyclesRem[prevInd][2] == 0)
						{
							CDB.request(prevInd, cyclesRem[prevInd][1]);
							cyclesRem[prevInd][0] = INVALID_LOC;
							cyclesRem[prevInd][1] = INVALID_VALUE;
							cyclesRem[prevInd][2] = 0;
						}
					}
					//if load, request on CDB 
					if(cyclesRem[ind][2] == 1)
					{
						CDB.request(ind, cyclesRem[ind][1]);
						cyclesRem[ind][0] = INVALID_LOC;
						cyclesRem[ind][1] = INVALID_VALUE;
						cyclesRem[ind][2] = 0;
					}
					//if store, write back and ready ROB for commit
					else
					{
						//pass in value and address to store to
						double[] tempStore = {cyclesRem[ind][1], (cyclesRem[ind][2]-4)};
						robrob.updateEntry(ind, tempStore);
						cyclesRem[ind][0] = INVALID_LOC;
						cyclesRem[ind][1] = INVALID_VALUE;
						cyclesRem[ind][2] = 0;
					}
				}
			}
				//subtract happens below
				
			///-----------------------------\
			//|*****************************|
			//|*********EXECUTE Stage*******|
			//|*****************************|
			//\-----------------------------/
				//subtract happens below
			
				//make
			//non-pipelined integer RS
			if((cycle - lastIntCycleExec) >= NUM_EXC_IA)
				res = iaRS.execute();
			else
				res[0] = INVALID_LOC;
			if(res[0] != INVALID_LOC)
			{
				lastIntCycleExec = cycle;
				cyclesRem[res[0]][0] = NUM_EXC_IA;
				cyclesRem[res[0]][1] = res[1];
				IST.editExCyc(res[0], cycle);
			}
			
			results = faRS.execute();
			if(results[0] != INVALID_LOC)
			{
				cyclesRem[(int)results[0]][0] = NUM_EXC_FPA;
				cyclesRem[(int)results[0]][1] = results[1];
				IST.editExCyc((int)results[0], cycle);
			}
			
			results = fmRS.execute();
			if(results[0] != INVALID_LOC)
			{
				cyclesRem[(int)results[0]][0] = NUM_EXC_FPM;
				cyclesRem[(int)results[0]][1] = results[1];
				IST.editExCyc((int)results[0], cycle);
			}
			
			results[0] = lsq.execute();
			if(results[0] != INVALID_LOC)
			{
				cyclesRem[(int)results[0]][0] = NUM_EXC_LSU;
				IST.editExCyc((int)results[0], cycle);
			}
			
				//check & send something to CDB
			for(int ind = 0; ind < NUM_ROB_ENT; ind++)
			{
				if(cyclesRem[ind][0] == 0 && cyclesRem[ind][2] == 0)
				{		
					if(!lsq.inLSQ(ind))
						CDB.request(ind, cyclesRem[ind][1]);
					
					if(iaRS.isBranch(ind))
					{
						//check if it was a taken branch
						if((bert.isBNE(ind) && cyclesRem[ind][1] != 0) || (!bert.isBNE(ind) && cyclesRem[ind][1]==0))
						{
							//if it branched correctly, just clear entry from brt
							if(btb.getValidity(bert.getPC(ind)) == 1 && (btb.getPrediction(bert.getPC(ind)) == bert.getBrAddr(ind)))
								bert.clear(ind);
							else //replace RAT and squash
							{											
								RAT = bert.getRat(ind);
								iaRS.squash(ind);
								faRS.squash(ind);
								fmRS.squash(ind);
								lsq.squash(ind);
								robrob.squash(ind);
								CDB.squash(ind);
								IST.squash(ind);
								
								//squash cyclesRem
								for(int num = ind+1; num < cyclesRem.length; num++)
								{
									cyclesRem[num][0] = INVALID_LOC;
									cyclesRem[num][1] = INVALID_VALUE;
									cyclesRem[num][2] = 0;
								}
														
								currInstr = bert.getBrAddr(ind);
								currRob = ind+1;
								btb.addPrediction(bert.getPC(ind), bert.getBrAddr(ind));
								bert.squash(ind);
								
								//tell system that it just squashed, so wait a cycle before fetching
								squashed = true;
							}
						}
						//not a taken branch
						else
						{
							//if it wasn't supposed to branch, just clear entry from brt
							if(btb.getValidity(bert.getPC(ind)) == 0)
								bert.clear(ind);
							else //replace RAT and squash
							{								
								RAT = bert.getRat(ind);
								iaRS.squash(ind);
								faRS.squash(ind);
								fmRS.squash(ind);
								lsq.squash(ind);
								robrob.squash(ind);
								CDB.squash(ind);
								IST.squash(ind);
								
								//squash cyclesRem
								for(int num = ind+1; num < cyclesRem.length; num++)
								{
									cyclesRem[num][0] = INVALID_LOC;
									cyclesRem[num][1] = INVALID_VALUE;
									cyclesRem[num][2] = 0;
								}
								
								currInstr = bert.getPC(ind)+1;	
								currRob = ind+1;
								btb.mispredictPC(bert.getPC(ind));
								bert.squash(ind);
								
								//tell system that it just squashed, so wait a cycle before fetching
								squashed = true;
							}
						}
					}
					
					cyclesRem[ind][0] = INVALID_LOC;
					cyclesRem[ind][1] = INVALID_VALUE;
					cyclesRem[ind][2] = 0;
				}
			}
			
				//subtract
			for(int ind = 0; ind < NUM_ROB_ENT; ind++)
			{
				if(cyclesRem[ind][0] > 0)
					cyclesRem[ind][0]--;
			}
			
			//Used for debugging to see the before and after
			if(DEBUG == 1)
				System.out.println(CDB.toString());

			///-----------------------------\
			//|*****************************|
			//|*******WRITE BACK Stage******|
			//|*****************************|
			//\-----------------------------/
				//results[0] is robLoc and results[1] is value to write back			
			results = CDB.getNextRequest();
			if(results[0] != INVALID_LOC)
			{
				IST.editWbCyc((int)results[0], cycle);
			
				iaRS.clear((int)results[0]);
				faRS.clear((int)results[0]);
				fmRS.clear((int)results[0]);
				lsq.clear((int)results[0]);
				
				iaRS.replace((int)results[0], (int)results[1]);
				faRS.replace((int)results[0], results[1]);
				fmRS.replace((int)results[0], results[1]);
				lsq.replace((int)results[0], results[1]);
				robrob.updateEntry((int)results[0], results[1]);
			}
			
			///-----------------------------\
			//|*****************************|
			//|*********ISSUE Stage*********|
			//|*****************************|
			//\-----------------------------/
			
			//don't read next instruction if the system just squashed
			if(squashed)
				squashed = false;
			else if(tokens[0].equals("LD") || tokens[0].equals("SD"))
			{
				if(!lsq.isFull())
				{
					arg1Loc = getRobIndex(RAT, tokens[1]);
					arg2Loc = getRobIndex(RAT, tokens[2].trim().substring((tokens[2].length()-3),(tokens[2].length()-1)));
					
					//if argLoc = INVALID_LOC, then it is in ARF somewhere
					//		otherwise check for a value in the ROB
					if(arg1Loc == INVALID_LOC)
						arg1Val = ARF[getArfIndex(tokens[1])];
					else if((robrob.getVal(arg1Loc)) != INVALID_VALUE)
					{
						arg1Val = robrob.getVal(arg1Loc);
						arg1Loc = INVALID_LOC;
					}
					if(arg2Loc == INVALID_LOC)
						arg2Val = ARF[getArfIndex(tokens[2].trim().substring((tokens[2].length()-3),(tokens[2].length()-1)))];
					else if((robrob.getVal(arg2Loc)) != INVALID_VALUE)
					{
						arg2Val = robrob.getVal(arg2Loc);
						arg2Loc = INVALID_LOC;
					}
					
					offset = Integer.parseInt(tokens[2].trim().substring(0, tokens[2].length()-4));
										
					if(tokens[0].equals("LD"))
					{
						regAddr = Integer.parseInt(tokens[1].trim().substring(1))+32;
						RAT[regAddr] = "ROB" + (currRob+1);
					}
					
					//for stores, adds rob entry with destination -1
					robrob.addEntry(regAddr);
					
					if(tokens[0].equals("LD"))
						lsq.addEntry('L', arg2Loc, (int)arg2Val, offset, arg1Loc, arg1Val, currRob);
					else
						lsq.addEntry('S', arg2Loc, (int)arg2Val, offset, arg1Loc, arg1Val, currRob);
					
					IST.addEntry(instBuff[currInstr]);
					IST.editIssCyc(currRob, cycle);
					currInstr++;
					currRob++;
				}
			}
			else if(tokens[0].equals("BEQ") || tokens[0].equals("BNE"))
			{
				if(!iaRS.isFull())
				{					
					prediction = btb.lookupPC(currInstr);
					arg1Loc = getRobIndex(RAT, tokens[1]);
					arg2Loc = getRobIndex(RAT, tokens[2]);
					
					if(arg1Loc == INVALID_LOC)
						arg1Val = ARF[getArfIndex(tokens[1])];
					else if((robrob.getVal(arg1Loc)) != INVALID_VALUE)
					{
						arg1Val = robrob.getVal(arg1Loc);
						arg1Loc = INVALID_LOC;
					}
					if(arg2Loc == INVALID_LOC)
						arg2Val = ARF[getArfIndex(tokens[2])];
					else if((robrob.getVal(arg2Loc)) != INVALID_VALUE)
					{
						arg2Val = robrob.getVal(arg2Loc);
						arg2Loc = INVALID_LOC;
					}
					
					offset = Integer.parseInt(tokens[3]);
					
					robrob.addEntry(regAddr);
					
					iaRS.addEntry(tokens[0], currRob, arg1Loc, arg2Loc, (int)arg1Val, (int)arg2Val);
					
					if(tokens[0].equals("BEQ"))
						bert.addEntry(currRob, RAT, currInstr, offset, 0);
					else
						bert.addEntry(currRob, RAT, currInstr, offset, 1);
					
					IST.addEntry(instBuff[currInstr]);
					IST.editIssCyc(currRob, cycle);
					if(prediction == INVALID_LOC)
						currInstr++;
					else
						currInstr = prediction;
					currRob++;
				}
			}
			else if(tokens[0].equals("ADD") || tokens[0].equals("SUB") || tokens[0].equals("ADDI"))
			{
				if(!iaRS.isFull())
				{
					regAddr = Integer.parseInt(tokens[1].trim().substring(1));

					arg1Loc = getRobIndex(RAT, tokens[2]);
					if(tokens[0].equals("ADDI"))
						arg2Val = Integer.parseInt(tokens[3]);
					else
					{
						arg2Loc = getRobIndex(RAT, tokens[3]);
						if(arg2Loc == INVALID_LOC)
							arg2Val = ARF[getArfIndex(tokens[3])];
						else if((robrob.getVal(arg2Loc)) != INVALID_VALUE)
						{
							arg2Val = robrob.getVal(arg2Loc);
							arg2Loc = INVALID_LOC;
						}
					}
					
					if(arg1Loc == INVALID_LOC)
						arg1Val = ARF[getArfIndex(tokens[2])];
					else if((robrob.getVal(arg1Loc)) != INVALID_VALUE)
					{
						arg1Val = robrob.getVal(arg1Loc);
						arg1Loc = INVALID_LOC;
					}
					
					//don't add noops to ROB
					if(!Arrays.equals(tokens, NOOP))
					{
						RAT[regAddr] = "ROB" + (currRob+1);
						robrob.addEntry(regAddr);
					
						iaRS.addEntry(tokens[0], currRob, arg1Loc, arg2Loc, (int)arg1Val, (int)arg2Val);
						IST.addEntry(instBuff[currInstr]);
						IST.editIssCyc(currRob, cycle);
						currInstr++;
						currRob++;
					}
				}
			}
			else if(tokens[0].equals("MULT.D"))
			{
				if(!fmRS.isFull())
				{
					regAddr = Integer.parseInt(tokens[1].trim().substring(1))+32;

					arg1Loc = getRobIndex(RAT, tokens[2]);
					arg2Loc = getRobIndex(RAT, tokens[3]);
									
					if(arg1Loc == INVALID_LOC)
						arg1Val = ARF[getArfIndex(tokens[2])];
					else if((robrob.getVal(arg1Loc)) != INVALID_VALUE)
					{
						arg1Val = robrob.getVal(arg1Loc);
						arg1Loc = INVALID_LOC;
					}
					if(arg2Loc == INVALID_LOC)
						arg2Val = ARF[getArfIndex(tokens[3])];	
					else if((robrob.getVal(arg2Loc)) != INVALID_VALUE)
					{
						arg2Val = robrob.getVal(arg2Loc);
						arg2Loc = INVALID_LOC;
					}
					
					RAT[regAddr] = "ROB" + (currRob+1);
					robrob.addEntry(regAddr);
					
					fmRS.addEntry(tokens[0], currRob, arg1Loc, arg2Loc, arg1Val, arg2Val);
					IST.addEntry(instBuff[currInstr]);
					IST.editIssCyc(currRob, cycle);
					
					currRob++;
					currInstr++;
				}
			}
			else
			{
				if(!faRS.isFull())
				{
					regAddr = Integer.parseInt(tokens[1].trim().substring(1))+32;

					arg1Loc = getRobIndex(RAT, tokens[2]);
					arg2Loc = getRobIndex(RAT, tokens[3]);		
				
					if(arg1Loc == INVALID_LOC)
						arg1Val = ARF[getArfIndex(tokens[2])];
					else if((robrob.getVal(arg1Loc)) != INVALID_VALUE)
					{
						arg1Val = robrob.getVal(arg1Loc);
						arg1Loc = INVALID_LOC;
					}
					if(arg2Loc == INVALID_LOC)
						arg2Val = ARF[getArfIndex(tokens[3])];	
					else if((robrob.getVal(arg2Loc)) != INVALID_VALUE)
					{
						arg2Val = robrob.getVal(arg2Loc);
						arg2Loc = INVALID_LOC;
					}
					
					RAT[regAddr] = "ROB" + (currRob+1);
					robrob.addEntry(regAddr);
					
					faRS.addEntry(tokens[0], currRob, arg1Loc, arg2Loc, arg1Val, arg2Val);
					IST.addEntry(instBuff[currInstr]);
					IST.editIssCyc(currRob, cycle);
					
					currRob++;
					currInstr++;
				}
			}
			
			//**DEBUG**
			//	print results at end of every cycle
			//	syntax is: {ARF/RAT, ROB, RS, LSQ, BTB, MEM, CYCLESREM, IST, CDB
			int[] printAr = {1,1,1,0,1,0,1,1,1};
			if(cycle != INVALID_LOC && DEBUG == 1) //print results after every cycle
				printResults(printAr, cycle, ARF, RAT, robrob, iaRS, faRS, fmRS, lsq, btb, bert, memory, cyclesRem, IST, CDB);
		}
		
		//Print output to file
		output.println(IST.toString());
		output.println(IST.toStringSCEC());
		output.println(registerFileToString(ARF));
		output.println(memToString(memory));
		
		output.close();
	}
	
	public static int getArfIndex(String reg)
	{
		if(reg.trim().charAt(0) == 'R')
			return Integer.parseInt(reg.substring(1));
		else
			return Integer.parseInt(reg.substring(1))+32;
	}
	
	//return rob index of location, returns -1 if in arf
	public static int getRobIndex(String[] rat, String reg)
	{
		int index;
		index = getArfIndex(reg);
		
		//if length is > 3, then ROB entry, not R or F entry
		if(rat[index].charAt(0) == 'R')
		{
			return Integer.parseInt(rat[index].substring(3))-1;
		}
		return INVALID_LOC;
	}
	
	public static String registerFileToString(double[] arf)
	{
		String s;
		s = "REGISTER VALUES\n";
		for(int i = 0; i < 32; i++)
		{
			s += "\t\tR" + i;
		}
		s += "\nvalue:  ";
		for(int i = 0; i < 32; i++)
		{
			s += arf[i];
			for(int j = 0; j < (8-String.valueOf(arf[i]).length()); j++)
				s+= " ";
		}
		s += "\n";
		for(int i = 0; i < 32; i++)
		{
			s += "\t\tF" + i;
		}
		s += "\nvalue:  ";
		for(int i = 32; i < 64; i++)
		{
			s += arf[i];
			for(int j = 0; j < (8-String.valueOf(arf[i]).length()); j++)
				s+= " ";
		}
		s += "\n";
		return s;
	}
	
	public static String memToString(double[] mem)
	{
		String s;
		s = "NON-ZERO MEMORY VALUES\nAddresses   Values\n";
		for(int i = 0; i < 64; i++)
		{
			if(mem[i] != 0)
				s += (i*4 + "\t\t\t" + mem[i] + "\n");
		}
		s += "\n";
		return s;
	}
	
	public static void printResults(int[] printArray, int cycle, double[] arf, String[] rat, ROB rob, IntRS iars, FloatingPtRS fars, FloatingPtRS fmrs, LSQ lsq, BTB btb, BRT bert, double[] mem, double[][] cycR, InstrST ist, cdbBuffer cdb)
	{
		System.out.println("The current cycle is: " + cycle + "\n");
		
		//print ARF and RAT
		if(printArray[0] != 0)
		{
			System.out.println("ARF\t\t\tRAT\nReg\tVal\t\tReg\tVal");
			for(int i = 0; i < 64; i++)
			{
				if(i <= 31)
					System.out.println("R" + i + "\t" + (int)arf[i] + "\t\tR" + i + "\t" + rat[i]);
				else
					System.out.println("F" + (i-32) + "\t" + arf[i] + "\t\tF" + (i-32) + "\t" + rat[i]);
			}
			System.out.println("\n");
		}
		
		//print ROB
		if(printArray[1] != 0)
		{
			System.out.println("ROB\nName\tARF Dest\tVal\tCommit");
			for(int i = 0; i < NUM_ROB_ENT; i++)
			{
				System.out.println("ROB" + (i+1) + "\t" + rob.getDest(i) + "\t\t" + rob.getVal(i) + "\t" + rob.getCommit(i));
			}
			System.out.print("\n\n");
		}
		
		//print Reservation Stations
		if(printArray[2] != 0)
		{
			System.out.println(iars.toString());
			System.out.println("Add " + fars.toString());
			System.out.println("Mult " + fmrs.toString());
		}
		
		//print LSQ
		if(printArray[3] != 0)
		{
			System.out.println(lsq.toString());
		}
		
		//print BTB
		if(printArray[4] != 0)
		{
			System.out.println("Branch Target Buffer\nEntry\tPredicted PC\tValid");
			for(int i = 0; i < 8; i++)
			{
				System.out.println(i + "\t" + btb.getPrediction(i) + "\t\t" + btb.getValidity(i));
			}
			System.out.println("\n" + bert.toString());
		}
		
		//print mem
		if(printArray[5] != 0)
		{
			System.out.println("Memory\nAddress\tValue");
			for(int i = 0; i < 64; i++)
			{
				System.out.println(i*4 + "\t" + mem[i]);
			}
		}
		
		//print cyclesRem
		if(printArray[6] != 0)
		{
			System.out.println("Entry\tCycles Remaining\tValue\tmem(1) or ex(0)");
			for(int i = 0; i < NUM_ROB_ENT; i++)
			{
				System.out.println(i + "\t" + (int)cycR[i][0] + "\t\t\t" + cycR[i][1] + "\t" + (int)cycR[i][2]);
			}
			System.out.println("");
		}
		
		//print IST
		if(printArray[7] != 0)
		{
			System.out.println(ist.toString());
		}	
		
		//print CDB
		if(printArray[8] != 0)
		{
			System.out.println(cdb.toString());	
		}
	}
}
