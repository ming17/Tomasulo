public class BTB
{
	private static int BTB_SIZE = 8;
	private btbEntry[] entries;
	
	public BTB()
	{
		entries = new btbEntry[BTB_SIZE];
	}
	
	public int lookupPC(int PC)
	{
		if(entries[PC%BTB_SIZE] != null && entries[PC%BTB_SIZE].takeBranch())
			return entries[PC%BTB_SIZE].predictedPC;
		return TomasuloRunner.INVALID_LOC;
	}
	
	public void addPrediction(int PC, int p)
	{
		if(entries[PC%BTB_SIZE] == null)
			entries[PC%BTB_SIZE] = new btbEntry(p);
		else
			entries[PC%BTB_SIZE].editPrediction(p);
	}
	
	public void mispredictPC(int PC)
	{
		if(entries[PC%BTB_SIZE] != null)
			entries[PC%BTB_SIZE].invalidate();
	}
	
	public int getPrediction(int PC)
	{
		int entry = PC%BTB_SIZE;
		if(entries[entry] != null)
			return entries[entry].getPrediction();
		return TomasuloRunner.INVALID_LOC;
	}
	
	public int getValidity(int PC)
	{
		int entry = PC%BTB_SIZE;
		if(entries[entry] != null && entries[entry].takeBranch())
			return 1;
		else
			return 0;
	}
	
	private class btbEntry 
	{
		private int predictedPC;
		private boolean branchTaken;
		
		private btbEntry(int p)
		{
			predictedPC = p;
			branchTaken = true;
		}

		private int getPrediction()
		{
			return predictedPC;
		}
		
		private void editPrediction(int p)
		{
			predictedPC = p;
			branchTaken = true;
		}
		
		private boolean takeBranch()
		{
			return branchTaken;
		}
		
		private void invalidate()
		{
			branchTaken = false;
		}
	}
}