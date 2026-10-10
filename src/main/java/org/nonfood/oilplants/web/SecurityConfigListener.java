package org.nonfood.oilplants.web;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import jakarta.servlet.ServletContextEvent;import jakarta.servlet.ServletContextListener;import jakarta.servlet.annotation.WebListener;
import java.sql.Driver;import java.sql.DriverManager;import java.sql.SQLException;import java.util.Enumeration;import java.util.logging.Level;import java.util.logging.Logger;
@WebListener public class SecurityConfigListener implements ServletContextListener{
 private static final Logger LOG=Logger.getLogger(SecurityConfigListener.class.getName());
 @Override public void contextInitialized(ServletContextEvent event){var cookie=event.getServletContext().getSessionCookieConfig();cookie.setHttpOnly(true);if("true".equalsIgnoreCase(System.getenv("SESSION_COOKIE_SECURE")))cookie.setSecure(true);}
 @Override public void contextDestroyed(ServletContextEvent event){
  Enumeration<Driver> drivers=DriverManager.getDrivers();while(drivers.hasMoreElements()){Driver d=drivers.nextElement();if(d.getClass().getName().startsWith("com.mysql.cj."))try{DriverManager.deregisterDriver(d);}catch(SQLException e){LOG.log(Level.FINE,"Unable to deregister MySQL driver",e);}}
  try{AbandonedConnectionCleanupThread.checkedShutdown();}catch(Exception e){LOG.log(Level.FINE,"Unable to stop MySQL cleanup thread",e);}
 }
}
