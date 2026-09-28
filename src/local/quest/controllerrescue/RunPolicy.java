package local.quest.controllerrescue;
final class RunPolicy {
 static boolean interrupted(String old,String current,boolean live){return old!=null&&!old.equals(current)&&!live;}
 static String interruption(String phase){return "Previous repair was interrupted after ["+phase+"]; connection not verified. No automatic retry. Use Check controller status.";}
}
