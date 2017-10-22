package oncserver;

public class Metric
{
	private String name;
	private int value;
	
	Metric(String name, int value)
	{
		this.name = name;
		this.value = value;
	}
	
	Metric(String name)
	{
		this.name = name;
		this.value = 0;
	}
	
	//getters
	String getName() { return name; }
	int getValue() { return value; }
	
	void incrementValue() { value++; }
}
