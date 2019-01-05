import java.math.*;

public class FloatingPtRS
{
	private FpRSEntry[] entries;
	private int numEntries;
	private int oldest;
	
	public FloatingPtRS(int size)
	{
		entries = new FpRSEntry[size];
		numEntries = 0;
		oldest = 0;
	}
		
	public void addEntry(String s, int r, int a1L, int a2L, double a1, double a2)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] == null)
			{
				entries[i] = new FpRSEntry(s, r, a1L, a2L, a1, a2);
				numEntries++;
				if(entries[oldest] == null)	
				{
					for(int j = 0; j < entries.length; j++)
					{	
						if(entries[j] != null)
						{
							oldest = j;
							break;
						}
					}
				}
				for(int j = 0; j < entries.length; j++)
				{
					if(entries[j] != null && entries[j].robLoc < entries[oldest].robLoc)
						oldest = j;
				}
				return;
			}
		}
	}
	
	public boolean isFull()
	{
		return numEntries == entries.length;
	}
	
	public double[] execute()
	{
		double[] results = {TomasuloRunner.INVALID_LOC, TomasuloRunner.INVALID_LOC};
		int ind;
		for(int i = 0; i < entries.length; i++)
		{
			ind = (i+oldest)%entries.length;
			if(entries[ind] != null && entries[ind].ready() && !entries[ind].executed)
			{
				results[0] = entries[ind].robLoc;
				results[1] = entries[ind].execute();
				entries[ind].executed = true;
				return results;
			}
		}
		return results;
	}
	
	public void clear(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && entries[i].robLoc == robEnt)
			{
				numEntries--;
				entries[i] = null;
				if(entries[oldest] == null)	
				{
					for(int j = 0; j < entries.length; j++)
					{	
						if(entries[j] != null)
						{
							oldest = j;
							break;
						}
					}
				}
				for(int j = 0; j < entries.length; j++)
				{
					if(entries[j] != null && entries[j].robLoc < entries[oldest].robLoc)
						oldest = j;
				}
				return;
			}
		}
	}
	
	public void squash(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && entries[i].robLoc > robEnt)
			{
				numEntries--;
				entries[i] = null;
			}
		}
	}
	
	public boolean ready()
	{
		for(int i = 0; i < entries.length; i++)
			if(entries[i] != null && entries[i].ready())
				return true;
		return false;	
	}
	
	public void replace(int robEnt, double data)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null)
				entries[i].replace(robEnt, data);
		}
	}

	public String toString()
	{
		String s = "Floating Point Reservation Stations\nType\tROB Dest\targ1 Loc\targ2 Loc\targ1\targ2\n";
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] == null)
				s += "EMPTY RS\n";
			else
			{
				s += (entries[i].op + "\tROB" + (entries[i].robLoc+1));
				if(entries[i].arg1Loc == TomasuloRunner.INVALID_LOC)
				{
					s += ("\t\t");
					if(entries[i].arg2Loc == TomasuloRunner.INVALID_LOC)
						s += ("\t\t\t\t" + entries[i].arg1 + "\t" + entries[i].arg2);
					else
						s += ("\t\tROB" + (entries[i].arg2Loc+1) + "\t" + entries[i].arg1);
				}
				else
				{
					s += ("\t\tROB" + (entries[i].arg1Loc+1) + "\t\t");
					if(entries[i].arg2Loc == TomasuloRunner.INVALID_LOC)
						s += ("\t\t\t" + entries[i].arg2);
					else
						s += ("ROB" + (entries[i].arg2Loc+1));
				}
				s += "\n";
			}
		}
		
		return s;
	}
	
	private static double round(double value, int places) {
			if (places < 0) throw new IllegalArgumentException();
 
			BigDecimal bd = new BigDecimal(Double.toString(value));
			bd = bd.setScale(places, RoundingMode.HALF_UP);
			return bd.doubleValue();
	}
	
	private class FpRSEntry {
		private String op;
		private int robLoc;
		private int arg1Loc;
		private int arg2Loc;
		private double arg1;
		private double arg2;
		private boolean readyForEx;
		private boolean executed;
		
		private FpRSEntry(String operation, int robEnt, int a1L, int a2L, double a1, double a2)
		{
			op = operation;
			robLoc = robEnt;
			arg1Loc = a1L;
			arg2Loc = a2L;
			arg1 = a1;
			arg2 = a2;
			executed = false;
			
			readyForEx = (arg1 != TomasuloRunner.INVALID_VALUE) && (arg2 != TomasuloRunner.INVALID_VALUE);
		}
		
		private boolean ready()
		{
			return readyForEx;
		}
		
		private void replace(int robEnt, double data)
		{
			if (arg1Loc == robEnt)
			{
				arg1Loc = TomasuloRunner.INVALID_LOC;
				arg1 = data;
			}
			if(arg2Loc == robEnt)
			{
				arg2Loc = TomasuloRunner.INVALID_LOC;
				arg2 = data;
			}
			
			readyForEx = (arg1 != TomasuloRunner.INVALID_VALUE) && (arg2 != TomasuloRunner.INVALID_VALUE); 
		}
		
		private double execute()
		{
			if (op.toUpperCase().equals("ADD.D"))
			{
				return round(arg1 + arg2, 15);
			}
			else if(op.toUpperCase().equals("SUB.D"))
			{
				return round(arg1 - arg2, 15);
			}
			else if(op.toUpperCase().equals("MULT.D"))
			{
				return round(arg1 * arg2, 15);
			}
			return Math.pow(2, 32);
		}
	}
}