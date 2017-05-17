package org.ciat.dssat_sum.control;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ciat.dssat_sum.control.OverviewWorker;
import org.ciat.dssat_sum.control.RunConfig;
import org.ciat.dssat_sum.model.LogFormatter;

public class App {

	public static Logger log;
	public static Properties prop;

	public static void main(String[] args) {
		App app = new App();
		log = app.obtainLogger();
		prop = app.obtainProperties();
		app.run();
	}

	private void run() {

		OverviewWorker owrk;
		SeriesWorker swrk;
		RunConfig rc = new RunConfig();

		log.fine("work started");

		owrk = rc.getOverviewWorker();
		owrk.work();
		log.fine("overview.csv created");
		swrk = rc.getSeriesWorker();
		swrk.work();
		log.fine("summary.csv created");

		log.fine("work finished");

	}

	private Logger obtainLogger() {
		/* Tag the log with the timestamp */
		long yourmilliseconds = System.currentTimeMillis();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
		Date resultdate = new Date(yourmilliseconds);
		String runName = sdf.format(resultdate);

		FileHandler fileHandler;
		ConsoleHandler consoleHandler;
		LogFormatter formatterTxt;
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		Handler[] handlers = logger.getHandlers();
		for (Handler handler : handlers) {
			logger.removeHandler(handler);
		}
		try {
			fileHandler = new FileHandler(runName + "_summary.log");
			consoleHandler = new ConsoleHandler();
			fileHandler.setLevel(Level.FINE);
			consoleHandler.setLevel(Level.FINE);
			formatterTxt = new LogFormatter();
			fileHandler.setFormatter(formatterTxt);
			consoleHandler.setFormatter(formatterTxt);
			logger.addHandler(fileHandler);
			logger.addHandler(consoleHandler);
			logger.setUseParentHandlers(false);
			logger.setLevel(Level.ALL);

		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return logger;
	}

	private Properties obtainProperties() {
		Properties prop = new Properties();
		File config = new File("config.properties");
		if (config.exists()) {
			try (FileInputStream in = new FileInputStream(config.getName())) {
				prop.load(in);

			} catch (IOException e) {
				App.log.severe(config + "not found");
			} catch (Exception e) {
				App.log.severe("Error reading configuration in file, please check the format");
			}

			return prop;

		} else {
			App.log.severe("Configuration not found in: " + config.getAbsolutePath());
		}

		return prop;
	}
}
