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

class ConnectionThread extends Thread {
	
	Socket socket;
	
	public ConnectionThread(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			String nextLine;
			String delimiter = "#";
			String[] tokens;
	        
			// read first line.
			nextLine = in.readLine();
			System.out.println(nextLine);
			
			if (nextLine.equals("upload"))
				out.println("ACK");
			else
				return;
			
			// read line with user data.
			nextLine = in.readLine();
			System.out.println(nextLine);
			tokens = nextLine.split(delimiter);
			
			for (int i = 0; i < tokens.length; i++)
				System.out.println(tokens[i]);
			
			boolean isNew = false;
			File file = null;
			
			while (!isNew) {
				String MACDelimiter = ":";
				String[] MACTokens = tokens[4].split(MACDelimiter);
				String filename = "logs\\";
				
				for (int i = 0; i < MACTokens.length; i++)
					filename += MACTokens[i];
				
				filename += "_" + new Date().getTime() + ".log";
				file = new File(filename);
				isNew = file.createNewFile();
			}
			
			FileWriter fstream = new FileWriter(file);
			BufferedWriter outFile = new BufferedWriter(fstream);
			
			// receive data and write it to file.
			nextLine = in.readLine();
			
			while (!nextLine.equals("finish")) {
				System.out.println(nextLine);
				outFile.write(nextLine);				
				nextLine = in.readLine();
			}
			outFile.close();
			fstream.close();
			out.close();
			in.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
}

public class TraceServer {
	
	public static void main(String args[]) {
		ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(5432);
			System.out.println(serverSocket.getInetAddress());
			while (true) {
				Socket clientSocket = serverSocket.accept();
				Thread connectionThread = new ConnectionThread(clientSocket);
				new Thread(connectionThread).start();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
