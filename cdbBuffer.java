public class cdbBuffer
{
	private int size;
	//requests in buf are in chronological order from head to tail
	private cdbRequest[] buf;
	private int head, tail;
	
	public cdbBuffer(int size)
	{
		buf = new cdbRequest[size];
		head = 0;
		tail = 0;
	}
	
	public void request(int rE, double v)
	{
		buf[tail] = new cdbRequest(rE, v);
		tail++;
		if(tail == buf.length)
			tail = 0;
	}
	
	public double[] getNextRequest()
	{
		double[] reqVals = {TomasuloRunner.INVALID_LOC,TomasuloRunner.INVALID_LOC};
		if(buf[head] != null)
		{
			reqVals[0] = (int)buf[head].getRobLoc();
			reqVals[1] = buf[head].getVal();
			buf[head] = null;
			head++;
			if(head == buf.length)
				head = 0;
		}
		return reqVals;
	}
	
	public void squash(int rE)
	{
		for(int i = 0; i < buf.length; i++)
		{
			if(buf[i] != null && buf[i].robLoc > rE)
			{
				buf[i] = null;
				//if squash entire CDB
				if(i == head)
				{
					head = 0;
					tail = 0;
					return;
				}
				else if(i > head && i < tail)
				{
					tail--;
					if(tail == -1)
						tail = buf.length-1;
				}
			}
		}
	}
	
	public String toString()
	{
		String s = "CDB\nRob\tVal\t\thead = " + head + "\n";
		for(int i = 0; i < buf.length; i++)
		{
			if(buf[i] != null)
				s += buf[i].robLoc + "\t" + buf[i].val + "\n";
			else
				s += "EMPTY\n";
		}	
		return s;
	}
	
	private class cdbRequest
	{
		private int robLoc;
		private double val;
		
		private cdbRequest(int robEnt, double v)
		{
			robLoc = robEnt;
			val = v;
		}
		
		private double getVal()
		{
			return val;
		}
		
		private int getRobLoc()
		{
			return robLoc;
		}
	}
}