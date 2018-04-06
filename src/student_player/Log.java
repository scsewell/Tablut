package student_player;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Formatter;;

/**
 * Singleton class with lazy instantiation for logging information. Used for
 * debugging purposes.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class Log
{
    private static final String LOG_DIR  = "logs/AI/";
    private static final String LOG_NAME = "PlayerLog";
    private static final String LOG_EXT  = ".log";

    private static Log          m_instance;

    private Logger              m_logger;
    private FileHandler         m_file;

    /**
     * Initializes writing to the log file.
     */
    public Log()
    {
        // Make sure the log directory exists
        File logDir = new File(LOG_DIR);

        if (!logDir.exists())
        {
            logDir.mkdir();
        }

        // Get a unique name for the log file
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname)
            {
                return pathname.getName().endsWith(LOG_EXT);
            }
        };
        
        int logCount = logDir.listFiles(filter).length;
        String logName = LOG_DIR + LOG_NAME + logCount + LOG_EXT;

        m_logger = Logger.getLogger(logName);

        try
        {
            m_file = new FileHandler(logName);
            m_file.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record)
                {
                    SimpleDateFormat logTime = new SimpleDateFormat("HH:mm:ss");
                    Calendar cal = new GregorianCalendar();
                    cal.setTimeInMillis(record.getMillis());
                    return logTime.format(cal.getTime()) + "\t" + record.getLevel() + "\t" + record
                            .getMessage() + System.lineSeparator();
                }
            });

            m_logger.addHandler(m_file);
        }
        catch (SecurityException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    
    /**
     * Gets the logger instance or creates one if non exisis.
     */
    private static Log instance()
    {
        if (m_instance == null)
        {
            m_instance = new Log();
        }
        return m_instance;
    }
    
    /**
     * Outputs a message to the log file.
     * 
     * @param object
     *            The object to write to the file.
     */
    public static void info(Object obj)
    {
        info(obj.toString());
    }
    
    /**
     * Outputs a message to the log file.
     * 
     * @param message
     *            The message to write to the file.
     */
    public static void info(String message)
    {
        // logging is disabled for competition, since it is not permitted to write files
        //instance().m_logger.info(message);
    }

    /**
     * Ouputs infromation on memory usage to the log.
     */
    public static void printMemoryUsage()
    {
        long KB = 1024;
        long MB = KB * 1024;

        String str = "";
        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryleft = Runtime.getRuntime().maxMemory() - memoryUsed;

        if (memoryUsed > MB)
        {
            str += String.format("Memory Used: %4.3f MB\t", (double)memoryUsed / MB);
        }
        else
        {
            str += String.format("Memory Used: %4.3f KB\t", (double)memoryUsed / KB);
        }

        if (memoryleft > MB)
        {
            str += String.format("Memory Remaining: %4.3f MB", (double)memoryleft / MB);
        }
        else
        {
            str += String.format("Memory Remaining: %4.3f KB", (double)memoryleft / KB);
        }

        info(str);
    }
}
