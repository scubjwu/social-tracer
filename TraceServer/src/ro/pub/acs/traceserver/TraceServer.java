package ro.pub.acs.traceserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

/**
 * Class for managing an incoming connection.
 * @author Radu Ioan Ciobanu
 */
class ConnectionThread extends Thread {
	
	Socket socket;
	boolean debug;
	
	/**
	 * Constructor for the ConnectionThread class.
	 * @param socket the socket connecting to the client
	 * @param debug boolean value for printing debug data
	 */
	public ConnectionThread(Socket socket, boolean debug) {
		this.socket = socket;
		this.debug = debug;
	}

	@Override
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			String nextLine;
			String delimiter = "#";
			String[] tokens;
	        
			// read the first line.
			nextLine = in.readLine();
			if (debug)
				System.out.println(nextLine);
			
			// exit if the message format is incorrect.
			if (nextLine.equals("upload"))
				out.println("ACK");
			else
				return;
			
			// read line with user data.
			nextLine = in.readLine();
			if (debug)
				System.out.println(nextLine);
			tokens = nextLine.split(delimiter);
			
			if (debug) {
				for (int i = 0; i < tokens.length; i++)
					System.out.println(tokens[i]);
			}
			
			// check for the correctness of the message.
			if (tokens.length < 4)
				return;
			
			boolean isNew = false;
			File file = null;
			
			// set name for the new log file and create it.
			while (!isNew) {
				String MACDelimiter = ":";
				String[] MACTokens = tokens[4].split(MACDelimiter);
				String filename = "";
				
				for (int i = 0; i < MACTokens.length; i++)
					filename += MACTokens[i];
				
				filename += "_" + new Date().getTime() + ".log";
				file = new File("logs", filename);
				isNew = file.createNewFile();
			}
			
			FileWriter fstream = new FileWriter(file);
			BufferedWriter outFile = new BufferedWriter(fstream);
			
			// receive data and write it to file.
			nextLine = in.readLine();
			while (!nextLine.equals("finish")) {
				if (debug)
					System.out.println(nextLine);
				outFile.write(nextLine);
				nextLine = in.readLine();
			}
			
			// close all open streams.
			outFile.close();
			fstream.close();
			out.close();
			in.close();
			
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}		
	}
}

/**
 * Class for the tracing server.
 * @author Radu Ioan Ciobanu
 */
public class TraceServer {
	
	/**
	 * Main method.
	 * @param args array of command line arguments
	 */
	public static void main(String args[]) {
		ServerSocket serverSocket;
		boolean debug = false;
		
		try {
			// create server socket on the 8080 port.
			serverSocket = new ServerSocket(8080);
			System.out.println(serverSocket.getInetAddress());
			
			// set debug value.
			if (args.length == 1 && args[0].equals("-v"))
				debug = true;
			
			while (true) {
				Socket clientSocket = serverSocket.accept();
				Thread connectionThread = new ConnectionThread(clientSocket, debug);
				new Thread(connectionThread).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Server error. Please restart.");
		}
	}
}
