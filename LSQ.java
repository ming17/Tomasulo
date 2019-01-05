public class LSQ
{
	private lsqEntry[] Q;
	private int head, numEntries;
	
	public LSQ(int size)
	{
		Q = new lsqEntry[size];
		head = 0;
		numEntries = 0;
	}

	public void addEntry(char t, int bL, int bV, int im, int rL, double rV, int robEnt)
	{
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] == null)
			{
				Q[i] = new lsqEntry(t, numEntries, bL, bV, im, rL, rV, robEnt);
				numEntries++;
				return;
			}
		}
	}
	
	public boolean isFull()
	{
		return numEntries == Q.length;
	}
	
	public boolean inLSQ(int robEnt)
	{
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] != null && Q[i].robLoc == robEnt)
			{
				return true;
			}
		}
		return false;
	}
	
	//return robLocation of load/store where address is calculated
	public int execute()
	{
		int h = head;
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[h] != null && Q[h].baseLoc == TomasuloRunner.INVALID_LOC && !Q[h].executed)
			{
				Q[h].execute();
				Q[h].executed = true;
				return Q[h].robLoc;
			}
			h++;
			if(h == Q.length)
				h = 0;
		}
		return TomasuloRunner.INVALID_LOC;
	}
	
	public void replace(int robEnt, double data)
	{
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] != null)
				Q[i].replace(robEnt, data);
		}
	}
	
	//fetch load/store if possible and remove from LSQ if load
	//	@returns 	double array of length 3. 
	//					-The first element is robLoc of instruction being updated.
	//					-The second element is the value that has been forwarded from a previous store. If this isn't TomasuloRunner.INVALID_VALUE then 
	//					 	memory forwarding worked, and takes one cycle. If it is TomasuloRunner.INVALID_VALUE then load must fetch from memory. 
	//					-The third entry is the calculated address for the entry
	//					-The fourth element is 0 for load, 1 for store
	public double[] memFetch()
	{
		double[] results = {TomasuloRunner.INVALID_LOC, TomasuloRunner.INVALID_VALUE, TomasuloRunner.INVALID_LOC, TomasuloRunner.INVALID_LOC};
		int h = head;
		for(int i = 0; i < Q.length; i++)
		{
			//Found a load that is ready to be fetched
			if(Q[h] != null && Q[h].addr != TomasuloRunner.INVALID_LOC && Q[h].type == 'L' && !Q[h].fetched)
			{
				boolean canFetch = true;
				double val = Q[h].loadVal;
				int mostRecentStore = 0;
				for(int j = 0; j < Q.length; j++)
				{
					if(Q[j] != null && Q[j].type == 'S' && Q[j].seq < Q[h].seq && Q[j].seq > mostRecentStore)
					{
						if(Q[j].addr == TomasuloRunner.INVALID_LOC)
							canFetch = false;
						else if(Q[j].addr == Q[h].addr)
						{
							if(Q[j].regVal != TomasuloRunner.INVALID_VALUE)
							{
								mostRecentStore = Q[j].seq;
								val = Q[j].regVal;
							}
						}
					}
				}
				if(canFetch)
				{
					results[0] = Q[h].robLoc;
					results[1] = val;
					results[2] = Q[h].addr;
					results[3] = 0;
					//clear load from lsq if it forwards from store
					if(val != TomasuloRunner.INVALID_VALUE)
						this.clear(Q[h].robLoc);
					else
						Q[h].fetched = true;
					return results;
				}
			}
			else if(Q[h] != null && Q[h].type == 'S' && Q[h].ready() && !Q[h].fetched) 
			{
				results[0] = Q[h].robLoc;
				results[1] = Q[h].regVal;
				results[2] = Q[h].addr;
				results[3] = 1;
				Q[h].fetched = true;
				return results;
			}
			h++;
			if(h == Q.length)
				h = 0;
		}
		return results;
	}
	
	public void commitStore(int robEnt)
	{
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] != null && Q[i].robLoc == robEnt)
			{
				for(int j = 0; j < Q.length; j++)
				{
					if(Q[j] != null && Q[j].addr == Q[i].addr && Q[j].type == 'L' && Q[j].seq > Q[i].seq)
						Q[j].loadVal = Q[i].regVal;
				}
				this.clear(Q[i].robLoc);
				return;
			}
		}
	}
	
	public void clear(int robEnt)
	{
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] != null && Q[i].robLoc == robEnt)
			{
				numEntries--;
				Q[i] = null;
				//update head if necessary
				if(i == head)
				{
					while(Q[head] == null)
					{
						head++;
						if(head == Q.length)
							head = 0;
						if(head == i)
						{
							head = 0;
							return;
						}
					}
				}
			}
		}
	}
	
	public void squash(int robEnt)
	{
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] != null && Q[i].robLoc > robEnt)
			{
				numEntries--;
				Q[i] = null;
				//update head if necessary
				if(i == head)
				{
					while(Q[head] == null)
					{
						head++;
						if(head == Q.length)
							head = 0;
						if(head == i)
						{
							head = 0;
							return;
						}
					}
				}
			}
		}
	}
	
	public String toString()
	{
		String s = ("Load Store Queue\nType\tSeq\tAddr\tVal\n");
		for(int i = 0; i < Q.length; i++)
		{
			if(Q[i] != null)
			{
				s+= Q[i].type + "\t" + Q[i].seq + "\t" + Q[i].addr + "\t";
				if(Q[i].type == 'S')
					s += Q[i].regVal;
				else
					s += Q[i].loadVal;
				s += "\n";
			}
			else
				s+= "EMPTY ENTRY\n";
		}
		return s;
	}
	
	private class lsqEntry
	{
		//for load or store, syntax is as follows:
		//Type  regLoc, offset(baseLoc)
		//Ld	F2, 	8(R1)
		private char type;		//Load or Store
		private int seq;		//Order in Q of Loads and Stores, chronological
		private int imm;
		private int regLoc;		//First register, F2 above
		private double regVal;	//Value to be stored for store, register to be loaded for load
		private int addr;
		private int baseLoc;	//Second register, R1 above
		private int baseVal;	//Where to store for store, where to load from for load
		private int robLoc;
		private double loadVal;	//Value to be loaded into register for load
		private boolean executed;
		private boolean fetched;
		
		//enter TomasuloRunner.INVALID_VALUE for vals if unknown, enter TomasuloRunner.INVALID_LOC for locations if unknown
		private lsqEntry(char t, int numE, int bL, int bV, int i, int rL, double rV, int robEnt)
		{
			type = Character.toUpperCase(t);
			seq = numE;
			baseLoc = bL;
			baseVal = bV;
			imm = i;
			regLoc = rL;
			regVal = rV;
			robLoc = robEnt;
			loadVal = TomasuloRunner.INVALID_VALUE;
			addr = TomasuloRunner.INVALID_LOC;
			executed = false;
			fetched = false;
		}
		
		//indicates if instruction is ready
		private boolean ready()
		{
			if(type == 'L')
				return addr != TomasuloRunner.INVALID_LOC && loadVal != TomasuloRunner.INVALID_VALUE;
			else
				return regLoc == TomasuloRunner.INVALID_LOC && addr != TomasuloRunner.INVALID_LOC;
		}
		
		private void replace(int robEnt, double data)
		{
			if(regLoc == robEnt)
			{
				regLoc = TomasuloRunner.INVALID_LOC;
				regVal = data;
			}
			if(baseLoc == robEnt)
			{
				baseLoc = TomasuloRunner.INVALID_LOC;
				baseVal = (int)data;
			}
		}
		
		//calculates address
		private void execute()
		{
			if((imm + baseVal) % 4 != 0)
				throw new IllegalArgumentException("Invalid Address being passed: " + imm+baseVal);
			addr = (imm + baseVal)/4;
		}
	}
}