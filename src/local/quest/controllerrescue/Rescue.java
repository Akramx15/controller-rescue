package local.quest.controllerrescue;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
final class Rescue {
    static final String NONCE=java.util.UUID.randomUUID().toString();
    static boolean checking=false;
    static boolean busy=false;
    static final long COOLDOWN=120000;
    static final int CAP=1048576;
    static synchronized boolean start(Context context,String source,Runnable completion){
        Context c=context.getApplicationContext();
        long now=System.currentTimeMillis(),last=c.getSharedPreferences("prefs",0).getLong("last",0);
        if(busy||checking){
            message(c,"A controller operation is already running.");
            return false;
        }
        if(last>0&&(now<last||now-last<COOLDOWN)){
            message(c,"Please wait: repairs have a 120-second cooldown.");
            return false;
        }
        busy=true;
        c.getSharedPreferences("prefs",0).edit().putLong("last",now).putString("activeNonce",NONCE).putString("phase","preflight checks").putLong("phaseTime",now).putLong("runTime",now).commit();
        message(c,"Checking root and sensor service…");
        new Thread(()->{
            File dir=null;
            String outcome="Repair failed.";
            try{
                File root=new File(c.getFilesDir(),"runs");
                root.mkdirs();
                File[] old=root.listFiles();
                if(old!=null){
                    Arrays.sort(old,Comparator.comparing(File::getName));
                    for(int i=0;
                    i<=old.length-10;
                    i++)remove(old[i]);
                }
                dir=new File(root,String.valueOf(now));
                if(!dir.mkdirs())throw new IOException("Cannot create private diagnostic directory");
                save(dir,"run.txt","start="+new Date()+"\nsource="+source+"\n");
                Result uid=run(dir,"01-root","id -u");
                if(uid.exit!=0||!uid.text.trim().equals("0"))throw new IOException("Root unavailable or not granted. The same HAL repair requires root. Manual computer fallback: adb reboot (not run by this app).");
                Result build=run(dir,"01-build","getprop ro.build.version.incremental");
                if(build.exit!=0||!build.text.trim().equals("52433670036000520"))throw new IOException("Repair stopped: this review build supports only tested headset build 52433670036000520.");
                Result power=run(dir,"01-power","dumpsys vrpowermanager");
                boolean mounted=false;
                for(String line:power.text.split("\\n"))if(line.trim().equals("State: HEADSET_MOUNTED"))mounted=true;
                if(power.exit!=0||!mounted)throw new IOException("Repair stopped: headset must be awake and mounted; sleep or unknown state is not treated as a fault.");
                Result before=run(dir,"02-service","getprop init.svc.vendor.oculus.sensors-hal-1-0");
                if(before.exit!=0||!before.text.trim().equals("running"))throw new IOException("Sensor service is not running; repair stopped without restarting it.");
                Result dump=run(dir,"03-before","dumpsys OVRRemoteService");
                if(dump.exit!=0||!dump.text.contains("ControllerGlue"))throw new IOException("Could not save controller state; repair stopped.");
                phase(c,"restart request about to execute");
                message(c,"Restarting sensor service. Tracking may briefly stop.");
                Result restart=run(dir,"04-restart","setprop ctl.restart vendor.oculus.sensors-hal-1-0");
                if(restart.exit!=0)throw new IOException("Restart request failed; inspect private logs.");
                phase(c,"restart command returned; waiting for tracking recovery");
                Thread.sleep(12000);
                phase(c,"verifying controller connections");
                Result state=run(dir,"05-service-after","getprop init.svc.vendor.oculus.sensors-hal-1-0");
                Result after=run(dir,"06-after","dumpsys OVRRemoteService");
                int connected=0;
                for(String line:after.text.split("\\n"))if(line.contains("Paired device:")&&line.contains("ExternalStatus: CONNECTED_ACTIVE"))connected++;
                outcome=state.exit==0&&state.text.trim().equals("running")&&after.exit==0&&connected==2?"Sensor service running; both controllers report connected. This is a snapshot, not a lasting-fix guarantee.":"Partial recovery: could not confirm the running sensor service and two active controllers. Press normal controller buttons and check again; do not repeatedly restart.";
            }
            catch(Exception e){
                outcome=e.getMessage()==null?e.toString():e.getMessage();
            }
            finally{
                if(dir!=null)try{
                    save(dir,"result.txt","end="+new Date()+"\n"+outcome);
                }
                catch(IOException ignored){
                }
                c.getSharedPreferences("prefs",0).edit().remove("activeNonce").putString("status",outcome).commit();
                message(c,outcome);
                synchronized(Rescue.class){
                    busy=false;
                    completion.run();
                }
            }
        }
        ,"controller-rescue").start();
        return true;
    }
    static void phase(Context c,String phase){
        c.getSharedPreferences("prefs",0).edit().putString("phase",phase).putLong("phaseTime",System.currentTimeMillis()).commit();
    }
    static synchronized void recoverInterrupted(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences("prefs",0);
        String old=p.getString("activeNonce",null);
        if(RunPolicy.interrupted(old,NONCE,busy)){
            String outcome=RunPolicy.interruption(p.getString("phase","unknown phase"));
            long time=p.getLong("runTime",0);
            File dir=new File(new File(c.getFilesDir(),"runs"),String.valueOf(time));
            if(dir.isDirectory())try{
                save(dir,"interrupted.txt","detected="+new Date()+"\nphaseTime="+p.getLong("phaseTime",0)+"\n"+outcome);
            }
            catch(IOException ignored){
            }
            p.edit().remove("activeNonce").putString("status",outcome).commit();
        }
    }
    static synchronized void checkStatus(Context context){
        Context c=context.getApplicationContext();
        if(busy||checking){
            message(c,"A controller operation is already running.");
            return;
        }
        checking=true;
        message(c,"Checking controller status (read only)…");
        new Thread(()->{
            String outcome;
            try{
                File dir=new File(c.getFilesDir(),"status-check");
                dir.mkdirs();
                Result uid=run(dir,"root","id -u");
                if(uid.exit!=0||!uid.text.trim().equals("0"))throw new IOException("Root unavailable: cannot read controller diagnostics. No repair attempted.");
                Result state=run(dir,"service","getprop init.svc.vendor.oculus.sensors-hal-1-0"),dump=run(dir,"controllers","dumpsys OVRRemoteService"),power=run(dir,"power","dumpsys vrpowermanager");
                int count=0;
                for(String line:dump.text.split("\\n"))if(line.contains("Paired device:")&&line.contains("ExternalStatus: CONNECTED_ACTIVE"))count++;
                String powerState="unknown";
                for(String line:power.text.split("\\n"))if(line.startsWith("State:"))powerState=line.substring(6).trim();
                outcome="Read-only status: service="+(state.exit==0?state.text.trim():"unavailable")+"; active controllers="+(dump.exit==0&&dump.text.contains("ControllerGlue")?String.valueOf(count):"unknown")+"; headset="+powerState+". No restart requested.";
            }
            catch(Exception e){
                outcome="Status check unavailable: "+e.getMessage()+". No restart requested.";
            }
            finally{
                synchronized(Rescue.class){
                    checking=false;
                }
            }
            message(c,outcome);
        }
        ,"controller-status-check").start();
    }
    static class Result{
        int exit;
        String text;
        Result(int e,String t){
            exit=e;
            text=t;
        }
    }
    static Result run(File dir,String name,String command)throws Exception{
        long start=System.currentTimeMillis();
        Process p=new ProcessBuilder("su","-c",command).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        Thread a=drain(p.getInputStream(),out),b=drain(p.getErrorStream(),err);
        boolean done=p.waitFor(20,TimeUnit.SECONDS);
        if(!done)p.destroyForcibly();
        a.join(1500);
        b.join(1500);
        String stdout;
        String stderr;
        synchronized(out){
            stdout=out.toString("UTF-8");
        }
        synchronized(err){
            stderr=err.toString("UTF-8");
        }
        save(dir,name+".stdout",stdout);
        save(dir,name+".stderr",stderr);
        save(dir,name+".meta","start="+start+"\nend="+System.currentTimeMillis()+"\ncommand=su -c "+command+"\nexit="+(done?p.exitValue():"timeout")+"\noutput_cap_bytes="+CAP);
        if(!done)throw new IOException("Root command timed out; no further commands were run.");
        return new Result(p.exitValue(),stdout);
    }
    static Thread drain(InputStream in,ByteArrayOutputStream out){
        Thread t=new Thread(()->{
            try{
                byte[] buf=new byte[8192];
                int n;
                while((n=in.read(buf))>=0){
                    synchronized(out){
                        int keep=Math.min(n,CAP-out.size());
                        if(keep>0)out.write(buf,0,keep);
                    }
                }
            }
            catch(IOException ignored){
            }
            finally{
                try{
                    in.close();
                }
                catch(IOException ignored){
                }
            }
        }
        );
        t.setDaemon(true);
        t.start();
        return t;
    }
    static void save(File dir,String name,String text)throws IOException{
        try(FileOutputStream f=new FileOutputStream(new File(dir,name))){
            f.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
    static void remove(File f){
        File[] children=f.listFiles();
        if(children!=null)for(File child:children)remove(child);
        f.delete();
    }
    static void message(Context c,String text){
        c.getSharedPreferences("prefs",0).edit().putString("status",text).apply();
        new Handler(Looper.getMainLooper()).post(()->Toast.makeText(c,UiStatus.friendly(text),Toast.LENGTH_LONG).show());
    }
}
