package com.example.authapp.services;

import controllers.AdminController;
import java.util.Timer;
import java.util.TimerTask;

/** Starts at most one daemon timer; the controller owns activation and disposal. */
public final class AdminRefreshService {
    private final AdminController controller;
    private Timer timer;
    public AdminRefreshService(AdminController controller){this.controller=controller;}
    public synchronized void start(){if(timer!=null)return;timer=new Timer("admin-refresh",true);timer.scheduleAtFixedRate(new TimerTask(){@Override public void run(){controller.refreshProductsList();}},10000,10000);}
    public synchronized void stop(){if(timer!=null){timer.cancel();timer.purge();timer=null;}}
    public synchronized boolean isRunning(){return timer!=null;}
    public void refreshNow(){controller.refreshProductsList();}
}
