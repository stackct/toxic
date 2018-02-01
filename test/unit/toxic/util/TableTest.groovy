package toxic.util

import org.junit.*

public class TableTest {
  
  @Test
  void should_return_empty_string_if_list_is_empty() {
    assert Table.toTable([]) == ""
  }

  @Test
  void should_merge_columns() {
    def rows = [
      [id:'foo-1', name:'bar'],
      [id:'foo-1', type:'baz']
    ]

    assert Table.getColumns(rows) == ['id': 5, 'name': 4, 'type': 4]
  }

  @Test
  void should_determine_column_size() {
    def rows = [
      [id:'foo-1', foo:'long-value'],
      [id:'foo-1', foo:'value'],
      [id:'foo-1', foo:'value', longColumnName:'x']
    ]

    assert Table.getPadding(rows, 'id')  == 5
    assert Table.getPadding(rows, 'foo') == 10
    assert Table.getPadding(rows, 'longColumnName') == 14
  }

  @Test
  void should_format_list_as_table() {
    def rows = [
      [name:'bar', id:'foo-1', type:'blah'],
      [name:'baz', id:'foo-2']
    ]

    def expected = new StringBuilder()
    expected.append('Id    | Name | Type\n')
    expected.append('----- | ---- | ----\n')
    expected.append('foo-1 | bar  | blah\n')
    expected.append('foo-2 | baz  |     \n')

    use (Table) {
      assert rows.toTable() == expected.toString()
    }
  }

  @Test
  void should_format_map_as_table() {
    def map = [
      id:  'some-long-value', 
      foo: 1, 
      bar: 'abc', 
      baz: 'something else'
    ]

    def expected = new StringBuilder()
    expected.append("Id  | some-long-value\n")
    expected.append("Foo | 1\n")
    expected.append("Bar | abc\n")
    expected.append("Baz | something else\n")

    use (Table) {
      assert map.toTable() == expected.toString()
    }
  }
}
