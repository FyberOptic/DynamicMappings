package net.fybertech.dynamicmappings;

import java.util.HashMap;
import java.util.Map;

public class ParmParser
{
	
	public class Parm
	{
		String key;			
		int paramCount;
		boolean found;
		String[] results;
		
		public Parm(String key, int parms)
		{
			this.key = key;				
			this.paramCount = parms;
			this.found = false;
			this.results = new String[parms];
			for (int n = 0; n < parms; n++) this.results[n] = "";
		}
		
		public String getFirstResult()
		{
			if (found && results.length > 0) return results[0];
			return null;
		}
	}
	
	public Map<String, Parm> parms = new HashMap<String, Parm>();
	
	Parm currentParm = null;
	int currentParmCount = 0;
	
	public Parm addParm(String key, int parms)
	{
		Parm p = new Parm(key, parms);
		this.parms.put(key, p);
		return p;
	}
	
	public Parm getParm(String key)
	{
		return this.parms.get(key);
	}
	
	public void processArgs(String[] args)
	{
		for (String arg : args)
		{
			if (currentParm == null)
			{
				currentParmCount = 0;
				currentParm = parms.get(arg);
				if (currentParm != null)
				{
					currentParm.found = true;
					if (currentParm.paramCount == 0) currentParm = null;
				}		
			}
			else
			{
				currentParm.results[currentParmCount++] = arg;
				if (currentParmCount >= currentParm.paramCount) currentParm = null; 
			}
		}
	}
}