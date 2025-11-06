//reading file in chunks to avoid string too long error
import groovy.json.JsonSlurper

   File file1 = new File(memory.fileOne)
   File file2 = new File(memory.fileTwo)

   file1.withReader { reader1 ->
       file2.withReader { reader2 ->
           def buffer1 = new char[1024]
           def buffer2 = new char[1024]
           int read1, read2

           while (true) {
            read1 = reader1.read(buffer1,0,1024)
            read2 = reader2.read(buffer2,0,1024)
                // if number of chars read differ, files are not identical
               if (read1 != read2) {
                  memory.isSame = false;
                  break;
               }
                //if end of both files are reached, files are identical
               if (read1 == -1) {
                  memory.isSame = true;
                  break;
               }
                //compare read chunks
               if(!Arrays.equals(buffer1,buffer2)){
                memory.isSame=false;
                break;
               }
           }
        
       }
   }

   

