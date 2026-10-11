package org.nonfood.oilplants.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Closes the JDBC pool when the WAR is undeployed or Tomcat stops. */
@WebListener
public final class DatabasePoolLifecycleListener implements ServletContextListener {
    @Override public void contextDestroyed(ServletContextEvent event){Database.shutdown();}
}
