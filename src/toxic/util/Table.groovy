package toxic.util

public class Table {

  /**
   * Produces a table representation string from a List of Maps. 
   */
  public static String toTable(List<Map> list) {
    
    if (list.size() == 0) return ""

    def columns = getColumns(list)
    def sb = new StringBuilder()

    buildHeaders(sb, columns)
    buildRows(sb, columns, list)
    
    sb.toString()
  }

  /**
   * Produces a table representation string from a Map
   */
  public static String toTable(Map map) {
    if (map.size() == 0) return ""

    int keyPad = map.collect { k,v -> k.size() }.max()
    map.collect { k,v -> "${k.capitalize().padRight(keyPad)} | ${v}" }.join('\n')
  }

  private static void buildHeaders(StringBuilder sb, Map columns) {
    columns.eachWithIndex { col, size, idx ->
      sb.append(col.capitalize().padRight(size))
      if (idx != columns.size()-1) sb.append(" | ")
    }

    sb.append("\n")

    columns.eachWithIndex { col, size, idx ->
      sb.append("-".multiply(size))
      if (idx != columns.size()-1) sb.append(" | ")
    }

    sb.append("\n")
  }

  private static void buildRows(StringBuilder sb, Map columns, List rows) {
    rows.each { row ->
      columns.eachWithIndex { col, size, idx ->
        sb.append((row[col]?.toString() ?: "").padRight(size))
        if (idx != columns.size()-1) sb.append(" | ")
      }

      sb.append("\n")
    }    
  }

  protected static Map<String,Integer> getColumns(List<Map> list) {
    def columns = [:]

    list.each { l ->
      l.each { k,v -> 
        if (!columns[k]) columns[k] = k.size()
        columns[k] = Math.max(columns[k], v.toString().size())
      }
    }

    columns.sort()
  }

  protected static int getPadding(List<Map> list, String column) {
    getColumns(list)[column]
  }
}