package oncserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataTable 
{
	private Column[] cols;
	private Row[] rows;
	
	DataTable(Column[] cols, Row[] rows)
	{
		this.cols = cols;
		this.rows = rows;
	}
	
	DataTable(List<Column> cols, List<Row> rows)
	{
		this.cols = cols.toArray(new Column[cols.size()]);
		this.rows = rows.toArray(new Row[rows.size()]);
	}
	
	//create a two column table where first column is a string and second column is  number
	DataTable(Map<String, Integer> dataMap)
	{
		//create the columns for a two column data table
		cols = new Column[2];
		cols[0] = new Column("string", "1", "Name");
		cols[1] = new Column("number", "2", "Value");
		
		List<Row> rowList = new ArrayList<Row>();
		for (Map.Entry<String, Integer> entry : dataMap.entrySet()) 
		{
			Cell[] cells = new Cell[2];
			cells[0] = new Cell(entry.getKey());
			cells[1] = new Cell(entry.getValue());
			
			rowList.add(new Row(cells));
		}
		
		rows = rowList.toArray(new Row[rowList.size()]);
	}
	
	DataTable(List<Metric> metricList)
	{
		//create the columns for a two column data table
		cols = new Column[2];
		cols[0] = new Column("string", "1", "Name");
		cols[1] = new Column("number", "2", "Value");
		
		List<Row> rowList = new ArrayList<Row>();
		for(Metric m : metricList)
		{
			Cell[] cells = new Cell[2];
			cells[0] = new Cell(m.getName());
			cells[1] = new Cell(m.getValue());
			
			rowList.add(new Row(cells));
		}
		
		rows = rowList.toArray(new Row[rowList.size()]);
	}
	
	//getters
	Column[] getColumnArray() { return cols; }
	Row[] getRowArray() { return rows; }
	
	class Column
	{
		private String type;
		private String id;
		private String label;
		
		Column(String type, String id, String label)
		{
			this.type = type;
			this.id = id;
			this.label = label;
		}
		
		//getters
		String getType() { return type; }
		String getID() { return id; }
		String getLabel() { return label; }
	}
	
	class Row
	{
		private Cell[] c;
		
		Row(Cell[] c)
		{
			this.c = c;
		}
		
		Cell[] getCellArray() { return c; }
	}
	
	class Cell
	{
		private Object v;
		private String f;
		
		Cell(int v)
		{
			this.v = v;
			this.f = Integer.toString(v);
		}
		
		Cell(String s)
		{
			this.v = s;
			this.f = s;
		}
		
		//getters
		Object getObject() { return v; }
		String getString() { return f; }
	}
}


