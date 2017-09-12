/** Brian Kasper **/

import java.applet.Applet;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JOptionPane;
import javax.swing.Timer;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendCellsRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesResponse;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.ValueRange;

public class FlappyFalcon extends Applet implements ActionListener, KeyListener
{
	private static final long serialVersionUID = 4301788152898391763L;
	final static int WIDTH = 800; // Applet Dimensions for debugging
	final static int HEIGHT = 800;
	final int space = 225, width = 100; // pipe Dimensions
	Rectangle falcon; // Flappy Falcon Player Object
	ArrayList<Rectangle> pipes;
	int xMotion, yMotion, score;
	boolean highscore, gameOver, started = false;
	static Random random; // Random variable to control pipe length
	static int[] leaderboardScores;
	static String[] leaderboardNames;
	static Timer timer; // timer for game actions
	private static final String APPLICATION_NAME = "Flappy Falcon"; // application name
	
	/** Code to save user credentials **/
	//private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
	//		".credentials/FlappyFalcon"); // Directory to store user credentials
	//private static FileDataStoreFactory DATA_STORE_FACTORY;
	
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static HttpTransport HTTP_TRANSPORT;
	String spreadsheetId = "10VXwPmbtBlHTBIv9GarB0cT-ZAgK-Ftmxpm6wUpDwoU"; // google sheets id for leaderboard
	String range = "Sheet1!A:B"; // range of variables to get from sheet
	private static List<String> scopes;
	Sheets service;
	ValueRange response;
	List<List<Object>> values;
	String name, token; // google profile user information
	Credential credential;

	static
	{
		try
		{
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			//DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t)
		{
			t.printStackTrace();
			System.exit(0);
		}
	}
	
	public void init()
	{	
		// call actionPerformed method every 0.015 seconds
		timer = new Timer(15, this);
		
		// frame
		this.setSize(WIDTH, HEIGHT);
		this.addKeyListener(this);

		// initialize Random and Leaderboard variables
		random = new Random();
		leaderboardNames = new String[5];
		leaderboardScores = new int[5];

		// create falcon and pipe objects
		falcon = new Rectangle(390, 390, 20, 20);
		pipes = new ArrayList<Rectangle>();

		// initialize four pipes
		for (int i = 0; i < 4; i++)
		{
			int height = 100 + random.nextInt(300); // pipe height
			pipes.add(new Rectangle(WIDTH + width + pipes.size() * 300, HEIGHT - height, width, height)); // top pipe
			pipes.add(new Rectangle(WIDTH + width + (pipes.size() - 1) * 300, 0, width, HEIGHT - height - space)); // bottom pipe
		}

		// initialize google scopes
		scopes = new ArrayList<String>();
		scopes.add("https://www.googleapis.com/auth/userinfo.profile"); // for user info
		scopes.add("https://www.googleapis.com/auth/spreadsheets"); // for spreadsheets

		// login to google
		try
		{
			service = getSheetsService();
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(null, "Error Loading Google Account!");
			signOut();
		}

		// load user name
		try
		{
			name = getUsername();
		} catch (IOException e)
		{
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Error Loading Username!");
			signOut();
		}

		// load spreadsheet
		try
		{
			loadSheet();
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(null, "Error Loading Spreadsheet!");
			e.printStackTrace();
			signOut();
			
		}
		
		// start timer
		timer.start();
	}

	public void paint(Graphics g)
	{
		// Create Background
		g.setColor(Color.cyan);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		// Create Falcon
		g.setColor(Color.red);
		g.fillRect(falcon.x, falcon.y, falcon.width, falcon.height);

		// Create pipes
		g.setColor(Color.green.darker());
		for (Rectangle pipe : pipes)
			g.fillRect(pipe.x, pipe.y, pipe.width, pipe.height);

		if (!started) // start screen
		{
			
			// title
			g.setColor(Color.WHITE);
			g.setFont(new Font("Arial", 1, 100));
			g.drawString("Flappy Falcon", 60, 150);

			// instructions
			g.setFont(new Font("Arial", 1, 50));
			g.drawString("Press 'A' to Start!", 185, 250);
			g.drawString("Press SPACE to Jump!", 115, 300);
			//g.drawString("Press 'S' to Sign Out!", 150, 350);
		}

		// show score and leaderboard if game is over
		if (gameOver)
		{
			started = false; // reset boolean

			// fill background
			g.setColor(Color.CYAN);
			g.fillRect(0, 0, WIDTH, HEIGHT);

			// draw instructions and score
			g.setFont(new Font("Arial", 1, 50));

			g.setColor(Color.RED);
			g.drawString("Game Over!", 250, 75);

			g.setColor(Color.BLUE);
			int x = 0;
			if(score < 10)
				x += 15;
			g.drawString("Your Score: " + score, 215 + x, 150);

			g.setColor(Color.BLACK);
			g.drawString("Leaderboard:", 235, 275);
			g.setFont(new Font("Arial", 1, 25));
			for (int i = 0; i < 5; i++)
			{
				g.drawString((i + 1) + ") " + leaderboardNames[i], 100, 350 + (45 * i));
				g.drawString(":", 500, 350 + (45 * i));
				g.drawString(Integer.toString(leaderboardScores[i]), 600, 350 + (45 * i));
			}
			g.setColor(Color.green.darker());
			g.setFont(new Font("Arial", 1, 50));
			g.drawString("Press 'A' to Play Again", 130, 690);
			//g.drawString("Press 'S' to Sign Out", 150, 750);
		}

		// draw score during game
		if (!gameOver && started)
		{
			g.setFont(new Font("Arial", 1, 50));
			g.setColor(Color.WHITE);
			g.drawString(String.valueOf(score), 375, 100);
		}
	}

	private void jump()
	{
		// if game is over reset falcon and pipes
		if (gameOver)
		{
			falcon = new Rectangle(WIDTH / 2 - 10, HEIGHT / 2 - 10, 20, 20);
			pipes.clear();
			yMotion = 0;

			for (int i = 0; i < 4; i++)
			{
				int height = 100 + random.nextInt(300); // pipe height
				pipes.add(new Rectangle(WIDTH + width + pipes.size() * 300, HEIGHT - height, width, height)); // top pipe
				pipes.add(new Rectangle(WIDTH + width + (pipes.size() - 1) * 300, 0, width, HEIGHT - height - space)); // bottom spipe
			}

		} else if (yMotion > 0) // if game is not over and y motion is positive
			yMotion = 0; // reset y motion

		yMotion -= 17; // simulate jump
	}

	private void loadSheet() throws IOException
	{
		// load values from doc
		response = service.spreadsheets().values().get(spreadsheetId, range).execute();
		values = response.getValues();
		if (values == null || values.size() == 0)
			JOptionPane.showMessageDialog(null, "Error Loading Document!");
		else
			loadLeaderboard();

	}

	private void loadLeaderboard()
	{
		int i = 0;
		for (List<?> row : values)
		{
			if (i < 5)
			{
				leaderboardNames[i] = (String) row.get(0);
				leaderboardScores[i] = (Integer.parseInt((String) row.get(1)));
				i++;
			}
		}
	}

	private void saveLeaderboard() throws IOException
	{
		// clear leaderboard
		ClearValuesRequest clearValuesRequest = new ClearValuesRequest();

		ClearValuesResponse clearValuesResponse = service.spreadsheets().values()
				.clear(spreadsheetId, range, clearValuesRequest).execute();

		System.out.println("Request \n\n");
		System.out.println(clearValuesRequest.toPrettyString());
		System.out.println("\n\nResponse \n\n");
		System.out.println(clearValuesResponse.toPrettyString());

		// set new values

		List<RowData> rowData = new ArrayList<RowData>();

		for (int i = 0; i < 5; i++)
		{
			List<CellData> cellData = new ArrayList<CellData>();

			CellData name = new CellData();
			name.setUserEnteredValue(new ExtendedValue().setStringValue(leaderboardNames[i]));
			cellData.add(name);

			CellData score = new CellData();
			score.setUserEnteredValue(new ExtendedValue().setNumberValue((double) leaderboardScores[i]));
			cellData.add(score);

			rowData.add(new RowData().setValues(cellData));
		}

		BatchUpdateSpreadsheetRequest batchRequests = new BatchUpdateSpreadsheetRequest();
		BatchUpdateSpreadsheetResponse batchResponse;
		List<Request> requests = new ArrayList<>();

		AppendCellsRequest appendCellReq = new AppendCellsRequest();
		appendCellReq.setFields("*");
		appendCellReq.setRows(rowData);

		requests = new ArrayList<Request>();
		requests.add(new Request().setAppendCells(appendCellReq));
		batchRequests = new BatchUpdateSpreadsheetRequest();
		batchRequests.setRequests(requests);

		batchResponse = service.spreadsheets().batchUpdate(spreadsheetId, batchRequests).execute();
		System.out.println("Request \n\n");
		System.out.println(batchRequests.toPrettyString());
		System.out.println("\n\nResponse \n\n");
		System.out.println(batchResponse.toPrettyString());
	}

	private void setLeaderboardScore(int playerScore)
	{

		highscore = false; // assume player did not score a high score

		// if in first place
		if (playerScore > leaderboardScores[0])
		{

			for (int i = 4; i > 0; i--)
				leaderboardScores[i] = leaderboardScores[i - 1];

			for (int i = 4; i > 0; i--)
				leaderboardNames[i] = leaderboardNames[i - 1];

			leaderboardScores[0] = playerScore;
			leaderboardNames[0] = null;

			highscore = true;

		} else // check if in second to fifth place
			for (int i = 1; i < 5; i++)
				if (playerScore <= leaderboardScores[i - 1] && playerScore > leaderboardScores[i])
				{
					for (int j = 4; j >= i; j--)
					{
						leaderboardScores[j] = leaderboardScores[j - 1];
						leaderboardNames[j] = leaderboardNames[j - 1];
					}
					leaderboardScores[i] = playerScore;
					leaderboardNames[i] = null;
					highscore = true;
					break;
				}

	}

	private void setLeaderboardName()
	{
		for (int i = 0; i < 5; i++)
			if (leaderboardNames[i] == null)
			{
				leaderboardNames[i] = name;
				break;
			}

	}

	private void authorize() throws IOException
	{
		// Load client secrets.
		InputStream in = FlappyFalcon.class.getResourceAsStream("client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, scopes).build();//.setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		token = credential.getAccessToken();
		//System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
	}

	private Sheets getSheetsService() throws IOException
	{
		authorize();
		return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
				.build();
	}

	private String getUsername() throws IOException
	{
		GoogleCredential user = new GoogleCredential().setAccessToken(token);
		Oauth2 oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, user).setApplicationName(APPLICATION_NAME)
				.build();
		Userinfoplus userinfo = oauth2.userinfo().get().execute();
		return userinfo.getName();
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		// increment x motion
		xMotion++;

		// actions performed during game
		if (started)
		{
			// move pipes left
			for (int i = 0; i < pipes.size(); i++)
			{
				Rectangle pipe = pipes.get(i);
				pipe.x -= 10;
			}

			// if xmotion is divisible by 2 and y motion is less than 15
			if (xMotion % 2 == 0 && yMotion < 15)
			{
				yMotion += 2; // add two to y motion to simulate gravity
			}

			// move falcon by y motion
			falcon.y += yMotion;

			// add and remove pipes
			for (int i = 0; i < pipes.size(); i++)
			{
				// get pipe
				Rectangle pipe = pipes.get(i);

				// if pipe is off the screen
				if (pipe.x + pipe.width < 0)
				{
					pipes.remove(pipe); // remove pipe off screen

					// if pipe touches end of screen
					if (pipe.y == 0)
					{
						int height = 100 + random.nextInt(300); // generate
																// random pipe
																// height
						pipes.add(new Rectangle(pipes.get(pipes.size() - 1).x + 600, HEIGHT - height, width, height)); // top
																														// pipe
						pipes.add(new Rectangle(pipes.get(pipes.size() - 1).x, 0, width, HEIGHT - height - space)); // bottom
																													// pipe
					}
				}
			}

			// check for colission
			for (Rectangle pipe : pipes)
			{
				// if falcon successfully passes through pipe add 1 to score
				if (pipe.y == 0 && falcon.x + falcon.width / 2 > pipe.x + pipe.width / 2 - 10
						&& falcon.x + falcon.width / 2 < pipe.x + pipe.width / 2 + 10)
					if (!gameOver)
						score++;

				// if falcon hits pipe end game
				if (pipe.intersects(falcon))
					gameOver = true;

				// if falcon goes off screen end game
				if (falcon.y > HEIGHT || falcon.y < 0)
					gameOver = true;
			}
		}

		// load and set leaderboard after game is over
		if (gameOver)
		{

			// set leaderboard
			setLeaderboardScore(score);
			if (highscore)
			{
				setLeaderboardName();
				try
				{
					saveLeaderboard();
				} catch (IOException e1)
				{
					JOptionPane.showMessageDialog(null, "Error Saving Leaderbord!");
				}
				timer.stop();
			}
		}

		// repaint screen
		repaint();

	}
	
	private void signOut()
	{
		/*File data = new File(DATA_STORE_DIR.getAbsolutePath() + "/StoredCredential");
		System.out.println(data.getAbsolutePath());
		data.delete();*/
		
		System.exit(0);
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		// if space key is pressed call jump method
		if (e.getKeyCode() == KeyEvent.VK_SPACE)
			jump();
		else if (e.getKeyCode() == KeyEvent.VK_A)
		{ // if a key is pressed
			if (!started)
			{ // and game is notstarted
				started = true; // start game
				jump();
				score = 0;
				gameOver = false;
				timer.start();
				repaint();
			}
		}
	}

}