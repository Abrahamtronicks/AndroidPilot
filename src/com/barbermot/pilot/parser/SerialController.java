package com.barbermot.pilot.parser;

import ioio.lib.api.exception.ConnectionLostException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import android.util.Log;

import com.barbermot.pilot.flight.FlightComputer;

public class SerialController implements Runnable {

	private static final String TAG = "SerialController";
	private long startSleep;
	private long sleepTime;
	private Parser parser;
	// private Scanner scanner;
	private PrintStream printer;
	private InputStream in;
	char delim;

	public SerialController(FlightComputer computer, char delim,
			InputStream in, PrintStream printer) throws ConnectionLostException {
		// scanner = new Scanner(in);
		// scanner.useDelimiter(Character.toString(delim));
		this.in = in;
		this.parser = new Parser(computer);
		this.printer = printer;
		this.delim = delim;
	}

	@Override
	public void run() {
		try {
			while (true) {
				executeCommand();
			}
		} catch (ConnectionLostException e) {
			Log.d(TAG, "Connection Lost");
			throw new RuntimeException(e);
		} catch (IOException e) {
			Log.d(TAG, e.getStackTrace().toString());
			throw new RuntimeException(e);
		}
	}

	public void executeCommand() throws IOException, ConnectionLostException {
		Log.d(TAG, "Execute Command");

		if (startSleep != 0) {
			if (System.currentTimeMillis() - startSleep < sleepTime) {
				return;
			} else {
				sleepTime = 0;
				startSleep = 0;
			}
		}

		// if (scanner.hasNext()) {
		// String cmd = scanner.next();
		StringBuffer sb = new StringBuffer();
		char c;
		Log.d(TAG, "About to read...");
		while ((c = (char) in.read()) != ';') {
			Log.d(TAG, "character: " + c);
			sb.append(c);
		}

		String cmd = sb.toString().trim();
		Log.d(TAG, "String: " + cmd);
		if (cmd.length() > 0) {
			printer.println(cmd);
			if (cmd.charAt(0) == 'z' || cmd.charAt(0) == 'Z') {
				int x = 0;
				String num = cmd.substring(1);
				num.trim();
				try {
					x = Integer.parseInt(num);
					sleepTime = x;
					startSleep = System.currentTimeMillis();
				} catch (NumberFormatException e) {
					parser.fail();
				}
			} else {
				parser.doCmd(cmd);
			}
		}
	}
}
