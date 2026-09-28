package local.quest.controllerrescue;
public class RunPolicyTest {
 public static void main(String[] args){
  if(!RunPolicy.interrupted("old-process","new-process",false))throw new AssertionError("new process must report interruption");
  if(RunPolicy.interrupted("same","same",false))throw new AssertionError("activity recreation not process death");
  if(RunPolicy.interrupted("old","new",true))throw new AssertionError("live worker protected");
  if(RunPolicy.interrupted(null,"new",false))throw new AssertionError("completed marker cleared");
  String message=RunPolicy.interruption("restart command returned; waiting for tracking recovery");
  if(!message.contains("connection not verified")||!message.contains("No automatic retry"))throw new AssertionError(message);
  System.out.println("5 interruption policy checks passed");
 }
}
