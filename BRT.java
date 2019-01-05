//Branch Recovery Table

public class BRT 
{
	private brtEntry[] entries;
	
	public BRT(int size)
	{
		entries = new brtEntry[size];
	}
	
	public String[] getRat(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && robEnt == entries[i].robLoc)
				return entries[i].RAT;
		}
		return null;
	}
	
	public int getPC(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && robEnt == entries[i].robLoc)
				return entries[i].pc;
		}
		return TomasuloRunner.INVALID_LOC;
	}
	
	public int getBrAddr(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && robEnt == entries[i].robLoc)
				return entries[i].branchAddr;
		}
		return TomasuloRunner.INVALID_LOC;
	}
	
	//call clear if branch was successfully calculated and can now be removed from BRT
	public void clear(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && entries[i].robLoc == robEnt)
			{
				entries[i] = null;
				return;
			}
		}
	}
	
	//call squash if branch was incorrectly calculated and all further branch entries should be removed from BRT
	public void squash(int robEnt)
	{
		if(robEnt == 0)
		{
			entries = new brtEntry[entries.length];
			return;
		}
		
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && entries[i].robLoc >= robEnt)
			{
				entries[i] = null;
			}
		}
	}
	
	public boolean isBNE(int robEnt)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] != null && entries[i].robLoc == robEnt)
			{
				return entries[i].bne == 1;
			}
		}
		return false;
	}
	
	public void addEntry(int r, String[] rat, int pc, int offset, int b)
	{
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] == null)
			{
				entries[i] = new brtEntry(r, rat, pc, offset, b);
				return;
			}
		}
	}
	
	public String toString()
	{
		String s = "Branch Recovery Table\nRob\tPC\tBrAddr\tbne(1) or beq(0)\n";
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] == null)
				s+= "EMPTY\n";
			else
			{
				s+= entries[i].robLoc + "\t" + entries[i].pc + "\t" + entries[i].branchAddr + "\t" + entries[i].bne + "\n";
				
				//prints RAT under other information, used for debugging
				/*s+= "RAT\nReg\tVal\n";
				for (int j = 0; j < 64; j++)
				{
					if(j <= 31)
						s += "R" + j + "\t" + entries[i].RAT[j] + "\n";
					else
						s += "F" + (j-32) + "\t" + entries[i].RAT[j] + "\n";
				}*/
			}
		}
		return s+"\n";
	}
	
	private class brtEntry
	{
		private int robLoc;
		private String[] RAT;
		private int pc;
		private int branchAddr;
		private int bne; //1 if type bne, 0 if type beq
		
		private brtEntry(int rob, String[] ra, int p, int offset, int bn)
		{
			robLoc = rob;
			RAT = new String[ra.length];
			//have to copy entries of rat, rather than point to same array
			for(int i = 0; i < ra.length; i++)
				RAT[i] = ra[i];
			pc = p;
			branchAddr = p+1+offset;
			bne = bn;
		}
	}
}