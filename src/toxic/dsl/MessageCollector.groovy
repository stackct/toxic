package toxic.dsl

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MessageCollector {
    private static final ThreadLocal<List<String>> messageHolder = new ThreadLocal<>();

    public static boolean add(String message){
        return getHolder().add("${LocalDateTime.now()} ${message}");
    }

    public static boolean addAll(List<String> messages){
        getHolder().add(LocalDateTime.now().toString())
        return getHolder().addAll(messages);
    }

    public static List<String> getMessages(){
       return getHolder()
    }

    public static void reset(){
        getHolder().clear();
    }

    private static List<String> getHolder() {
        if(!messageHolder.get()){
            messageHolder.set(new ArrayList<String>())
        }
        return messageHolder.get()
    }
}
