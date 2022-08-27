import enumerate.Action;

public class move {
    Action action;
    int counter=0;
    move(Action action){
        this.action = action;
    }
    void increase_counter(Action action){
        if(this.action==action){
            counter++;
        }
    }
    public String toString(){
        return action.name() + " : "+counter;
    }
}
