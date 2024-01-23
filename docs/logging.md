Certainly! Here's a short example of configuring logging in Java using the build-in java.util.logging

import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggingExample{
    public static void main(String[] args){
        Logger logger = Logger.getLogger(LoggingExample.class.getName());

        //Configure console handler
        logger.addHandler(new java.util.logging.ConsoleHandler());

        //Configure file handler
        try{
            FileHandler fileHandler = new FileHandler("app.log");
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (Exception e){
            logger.log(Level.SEVERE, "Error configuring file handler", e);
        }
        // Set log levels
        logger.setLevel(Level.ALL);
        logger.getHandlers()[0].setLevel(Level.INFO); // Console handler to display INFO level and above
        logger.getHandlers()[1].setLevel(Level.ALL); // File handler to log all levels

        // Log messages
        logger.finest("Finest level message");
        logger.finer("Finer level message");
        logger.fine("Fine level message");
        logger.config("Config level message");
        logger.info("Info level message");
        logger.warning("Warning level message");
        logger.severe("Severe level message")

    }
}

This example sets up both console and file handlers, each with different log levels then You can customize the file name, log levels, and other settings based on your requirements.