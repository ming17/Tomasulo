public class ROB
{
	private robEntry[] entries;
	private int head, tail;
	
	public ROB(int s)
	{
		entries = new robEntry[s];
		head = 0;
		tail = 0;
	}
	
	public void addEntry(int d)
	{
		entries[tail] = new robEntry(d);
		tail++;
	}
	
	public void updateEntry(int entry, double value)
	{
		if(entry != TomasuloRunner.INVALID_LOC)
			entries[entry].updateVal(value);
	}
	
	public void updateEntry(int entry, double[] storeVals)
	{
		if(entry != TomasuloRunner.INVALID_LOC)
			entries[entry].updateVal(storeVals);
	}
	
	public double[] commit()
	{
		double[] temp = {TomasuloRunner.INVALID_LOC, TomasuloRunner.INVALID_LOC, TomasuloRunner.INVALID_LOC};
		if(entries[head] != null && entries[head].canCommit())
		{
			head++;
			temp[0] = head-1;
			temp[1] = entries[head-1].getDest();
			temp[2] = entries[head-1].getVal();
			//entries[head-1] = new robEntry(-1);
			return temp;
		}
		
		return temp;
	}
	
	public int getDest(int entry)
	{
		if(entries[entry] != null)
		{
			if(entries[entry].isStore())
			{
				return (int)((entries[entry].storeAddr+4)*1000);
			}
			else
				return entries[entry].getDest();
		}
		return TomasuloRunner.INVALID_LOC;
	}
	
	public double getVal(int entry)
	{
		if(entries[entry] == null)
			return TomasuloRunner.INVALID_VALUE;
		return entries[entry].getVal();
	}
	
	public char getCommit(int entry)
	{
		if(entries[entry] != null && entries[entry].canCommit())
			return 'Y';
		else
			return 'N';
	}
	
	//clears all ROB entries after entry
	public void squash(int entry)
	{
		for(int i = entry+1; i < entries.length; i++)
			entries[i] = null;
		tail = entry+1;
	}
	
	public boolean doneExecuting()
	{
		return head != 0 && head == tail;
	}
	
	private class robEntry
	{
		private int dest;
		private double val;
		private double storeAddr;
		private boolean commit;
		
		private robEntry(int arfDest)
		{
			dest = arfDest;
			val = TomasuloRunner.INVALID_VALUE;
			storeAddr = TomasuloRunner.INVALID_LOC;
			commit = false;
		}
		
		private int getDest()
		{
			if(this.isStore())
			{
				return (int)((storeAddr+4)*1000);
			}
			else
				return dest;
		}
		
		private void updateVal(double v)
		{
			if(v != TomasuloRunner.INVALID_VALUE)
			{
				val = v;
				commit = true;
			}
		}
		
		private void updateVal(double[] sV)
		{
			if(sV[0] != TomasuloRunner.INVALID_VALUE)
			{
				val = sV[0];
				storeAddr = sV[1];
				commit = true;
			}
		}
		
		private double getVal()
		{
			return val;
		}
		
		private boolean isStore()
		{
			return storeAddr != TomasuloRunner.INVALID_LOC;
		}
		
		private boolean canCommit()
		{
			return commit;
		}
		
	}

}