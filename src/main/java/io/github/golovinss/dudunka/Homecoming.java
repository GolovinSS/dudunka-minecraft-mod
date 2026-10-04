package io.github.golovinss.dudunka;

/** Transient home-presence samples. Gaps/reloads never turn into an excursion. */
public final class Homecoming {
    private long last=Long.MIN_VALUE,pendingUntil,nextWelcome;
    private int awayTicks;
    private boolean seenHome;
    public void reset(){last=Long.MIN_VALUE;pendingUntil=0;awayTicks=0;seenHome=false;}
    public void observe(long now,double distanceSquared){
        if(now==last)return;
        if(last==Long.MIN_VALUE || now-last!=20){reset();last=now;seenHome=distanceSquared<=64;return;}
        last=now;
        if(distanceSquared<=64){
            if(seenHome && awayTicks>=200 && now>=nextWelcome)pendingUntil=now+400;
            seenHome=true;awayTicks=0;
        } else if(distanceSquared>144){
            if(seenHome)awayTicks=Math.min(200,awayTicks+20);
            pendingUntil=0;
        } else if(awayTicks<200){awayTicks=0;} // Preserve a completed excursion while walking back through the doorway.
    }
    public boolean pending(long now){return pendingUntil>now && now>=nextWelcome;}
    public boolean ready(long now){return pending(now) || seenHome && awayTicks>=200 && now>=nextWelcome;}
    public boolean consumeArrival(long now){if(!ready(now))return false;awayTicks=0;pendingUntil=0;nextWelcome=now+1200;return true;}
    public boolean consume(long now){if(!pending(now))return false;pendingUntil=0;nextWelcome=now+1200;return true;}
}
