public class InstrST
{
	private int numInstr;
	//Entry index matches rob entry index
	private InstrSTEntry[] entries;
	
	public InstrST(int size)
	{
		entries = new InstrSTEntry[size];
		numInstr = 0;
	}
	
	public void addEntry(String s)
	{
		entries[numInstr] = new InstrSTEntry(s);
		numInstr++;
	}
	
	public void squash(int entry)
	{
		for(int i = entry+1; i < entries.length; i++)
		{
			entries[i] = null;
		}
		numInstr = entry+1;
	}
	
	public void editIssCyc(int entry, int val)
	{
		entries[entry].issCyc = val;
	}
	
	public void editExCyc(int entry, int val)
	{
		entries[entry].exCyc = val;
	}
	
	public void editMemCyc(int entry, int val)
	{
		entries[entry].memCyc = val;
	}
	
	public void editWbCyc(int entry, int val)
	{
		entries[entry].wbCyc = val;
	}
	
	public void editCommitCyc(int entry, int val)
	{
		entries[entry].commitCyc = val;
	}
	
	public String toString()
	{
		String s;
		s = "                        ISSUE   EX\tMEM\tWB\tCOMMIT\n";
		for(int i = 0; i < numInstr; i++)
		{
			s += entries[i].instr;
			for(int j = 0; j < (24-entries[i].instr.length()); j++)
				s+= " ";
			s+= entries[i].issCyc + "    \t" + entries[i].exCyc + "\t" + entries[i].memCyc + "\t" + entries[i].wbCyc + "\t" + entries[i].commitCyc+ "\n";
		}
		return s;
	}
	
	public String toStringSCEC()
	{
		String s;
		s = "\t\t\t\t\tstart cycle-end cycle\n";
		for(int i = 0; i < numInstr; i++)
		{
			s += entries[i].instr;
			for(int j = 0; j < (20-entries[i].instr.length()); j++)
				s+= " ";
			s += entries[i].issCyc + "-" + entries[i].commitCyc + "\n";
		}
		return s;
	}
	
	private class InstrSTEntry
	{
		private String instr;
		private int issCyc;
		private int exCyc;
		private int memCyc;
		private int wbCyc;
		private int commitCyc;
		
		private InstrSTEntry(String s)
		{
			instr = s;
			issCyc = exCyc = memCyc = wbCyc = commitCyc	= 0;
		}	
	}
}