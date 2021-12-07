/*
 EWouldsPlayer - An expectimax implementation of a player for poker squares.
 This player tries to make poker hands to get a flush in the rows and straights in the columns,
 it makes those plays based of the chance that the hand can be made in the given column or row
 Author: Eric Woulds
 Code Modified from RandomMCPlayer by: Todd W. Neller with modifications made by: Michael W. Fleming
*/

import java.util.ArrayList;
import java.util.Random;

public class EWouldsPlayer implements PokerSquaresPlayer {
	
	private PokerSquaresPointSystem system;
	private final int SIZE = 5;// number of rows/columns in square grid
	private final int NUM_POS = SIZE * SIZE;// number of positions in square grid
	private int depthLimit = 1;
	private final int NUM_CARDS = Card.NUM_CARDS; // number of cards in deck
	private Random random = new Random();
	private int[] plays = new int[NUM_POS];//positions of plays so far (index 0 through numPlays - 1) recorded as integers using row-major indices.
	// row-major indices: play (r, c) is recorded as a single integer r * SIZE + c (See http://en.wikipedia.org/wiki/Row-major_order)
	// From plays index [numPlays] onward, we maintain a list of yet unplayed positions.
	private int numPlays = 0;
	
	private Card[][] grid = new Card[SIZE][SIZE];// grid with Card objects or null (for empty positions)
	private Card[] simDeck = Card.getAllCards();// a list of all Cards. As we learn the index of cards in the play deck,
	// we swap each dealt card to its correct index.  Thus, from index numPlays
	// onward, we maintain a list of undealt cards for expectimax calculations.
	
	private int[] legalPlayList = new int[NUM_POS]; //stores legal play list, indexed by numPlays
	
	//Create an EWoulds Player
	public EWouldsPlayer(){}
	
	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#setPointSystem(PokerSquaresPointSystem, long)
	 */
	@Override
	public void setPointSystem(PokerSquaresPointSystem system, long millis) {
		this.system = system;
	}
	
	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#init()
	 */
	@Override
	public void init() {
		//clear grid
		for (int row = 0; row < SIZE; row++)
			for (int col = 0; col < SIZE; col++)
				grid[row][col] = null;
		// reset numPlays
		numPlays = 0;
		// (re)initialize list of play positions (row-major ordering)
		for (int i = 0; i < NUM_POS; i++)
			plays[i] = i;
	}
	
	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#getPlay(Card, long)
	 */
	@Override
	public int[] getPlay(Card card, long millisRemaining) {
		/*
		 * The player chooses the best legal play based of the highest expected scoring outcome for the given card
		 */
		int cardIndex = numPlays;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[numPlays];
		simDeck[numPlays] = card;
		
		if(numPlays < 24){
			int remainingPlays = NUM_POS -numPlays;
			ArrayList<Integer> bestPlays = new ArrayList<Integer>();
			double curBestExpectimaxVal = Double.NEGATIVE_INFINITY;
			
			System.arraycopy(plays, numPlays, legalPlayList, 0, remainingPlays);
			
			for(int i = 0; i < remainingPlays; i++){
				int curPlay = legalPlayList[i];
				double curExpectimax = expectimax(card, curPlay/SIZE, curPlay % SIZE, depthLimit);
				
				//if the curExpectimax is greater or equal we add it to the list of best plays
				if(curExpectimax >= curBestExpectimaxVal){
					//found a better current Best value, clear the previous best plays first adding to best plays
					if(curExpectimax > curBestExpectimaxVal){
						bestPlays.clear();
					}
					bestPlays.add(curPlay );
					curBestExpectimaxVal = curExpectimax;
				}
				int bestPlay = bestPlays.get(random.nextInt(bestPlays.size())); // Choosing the best play, ties are broken randomly
				int index = numPlays;
				while (plays[index] != bestPlay){
					index++;
				}
				plays[index] = plays[numPlays];
				plays[numPlays] = bestPlay;
			}
		}
		int[] playPos = {plays[numPlays] / SIZE, plays[numPlays] % SIZE};
		makePlay(card, playPos[0], playPos[1]);
		return playPos;
	}
	
	/**
	 * Calculates the expectimax value for a card placement
	 * @param card - card to be placed
	 * @param row - row index for placement of card
	 * @param col - column index for placement of card
	 * @param depthLimit - recursion limit
	 * @return returns the expectimax value for the card placement
	 */
	private double expectimax(Card card, int row, int col, int depthLimit) {
		double expectimax = Double.NEGATIVE_INFINITY;
		
		//temp play
		makePlay(card,row,col);
		
		if(depthLimit == 0){
			expectimax = expectimaxEval();
		}else{
			int[] legalPlays = new int[NUM_POS];
			int playsLeft = NUM_POS - numPlays;
			if(playsLeft == 0){
				expectimax = system.getScore(grid);
				
				undoPlay();
				return expectimax;
			}
			System.arraycopy(plays, numPlays,legalPlays,0,playsLeft);
			for (int i = 0; i < playsLeft; i++){
				double newExpectimaxVal = 0;
				int nextRow = legalPlays[i] / 5;
				int nextCol = legalPlays[i] % 5;
				
				for(int j = numPlays; j < NUM_CARDS; j++){
					//recursive call
					newExpectimaxVal += expectimax(simDeck[j], nextRow, nextCol, depthLimit - 1);
				}
				//Change newExpectimaxVal based off the total number of cards and the current plays made
				newExpectimaxVal /= (NUM_CARDS - numPlays);
				
				//update expectimax with new best
				if(newExpectimaxVal > expectimax){
					expectimax = newExpectimaxVal;
				}
			}
		}
		undoPlay();
		return expectimax;
	}
	
	/**
	 * @return a score based of the current game state
	 */
	public double expectimaxEval(){
		double eval = 0;
		int[] curHandScore = system.getHandScores(grid);
		for(int i = 0; i < 2 * SIZE; i++){
			int score = curHandScore[i];
			//hand is already made so add its score to result
			if(score > 0){
				eval += curHandScore[i];
			}else{
				//Checking for any potential plays
				if(i < 5){
					//checking rows
					eval += GridEval(i, 0);
				}else{
					//checking columns
					eval += GridEval(0, i - 5);
				}
			}
			
		}
		return eval;
	}
	
	/**
	 * Calculates the score that a given row or column will add to the overall result
	 * @param row - row to be evaluated, will be 0 if checking columns
	 * @param col - col to be evaluated, will be 0 if checking rows
	 * @return score of the row or column that was evaluated
	 */
	public double GridEval(int row, int col){
		double score = 0;
		ArrayList<Card> cards = new ArrayList<Card>();
		
		//checking the rows
		if(col == 0){
			for (int i = 0; i < SIZE; i++) {
				Card card = grid[row][i];
				
				if (card != null) {
					cards.add(grid[row][i]);
				}
			}
			score += getStraightScore(cards);
		}else if(row == 0){//checking the column
			for (int i = 0; i < SIZE; i++) {
				Card card = grid[i][col];
				
				if (card != null) {
					cards.add(grid[i][col]);
				}
			}
			score += getFlushScore(cards);
		}
		return score;
	}
	
	/**
	 * Evaluates the chance for a flush with any cards that are given to it,
	 * each accepted card adds one to the final result if it has. If there is no chance
	 * for a flush a 0 will be returned
	 * @param cards - current cards to be evaluated
	 * @return score based off of the current accepted cards, if any. The higher the score the more
	 * chance that flush is possible
	 */
	public double getFlushScore(ArrayList<Card> cards){
		int flushScore = system.getScoreTable()[PokerHand.FLUSH.id];
		int currentSuit = -1; //-1 indicates no suit set yet
		int suitCount = 0;
		double score = 0;
		
		for (Card card : cards){
			//simple check if the cards suits match current suit
			if (card.getSuit() == currentSuit || currentSuit == -1){
				suitCount += 1;
				currentSuit = card.getSuit();
			}else {
				suitCount = 0;
				break;
			}
		}
		//Adds a fractional score of a flush based off the amount of accepted cards
		if (suitCount > 1) {
			score = flushScore * ((float)suitCount / SIZE);
		}else if (suitCount == 1) {//if only one card of a suit we encourage the player to place cards of that suit here
			score = flushScore * ((float)suitCount / SIZE / 2);
		}
		
		return score;
	}
	
	/**
	 *Evaluates the chance for a Straight with any cards that are given to it,
	 *each accepted card adds one to the final result if it has. If there is no chance
	 *for a straight a 0 will be returned
	 * @param cards - current cards to be evaluated
	 * @return score based off of the current accepted cards, if any. The higher the score the more
	 * 	 * chance that a straight is possible
	 */
	public double getStraightScore(ArrayList<Card> cards){
		int straightScore = system.getScoreTable()[PokerHand.STRAIGHT.id];
		int maxRank = -1;
		int minRank = 13;
		int lowerRank = 0;
		int higherRank = 12;
		double score = 0; //if 0, lost chance for straight
		
		for(Card card: cards){
			int curRank = card.getRank();
			//checking if we should count ace as high or low
			if(maxRank == 0 && minRank == 0){
				if (curRank >= 9) {
					maxRank = 13;
					minRank = 13;
				}else if (curRank <= 5){// counting ace as low
					maxRank = 0;
					minRank = 0;
				}else {// lost chance for straight
					score = 0;
					break;
				}
			}
			//count ace as high
			if(maxRank >= 9 && curRank == 0){
				curRank = 13;
			}
			if(score == 0 || (curRank >= lowerRank && curRank <= higherRank)){
				score += 1;
				if(curRank > maxRank){
					maxRank = curRank;
				}
				if(curRank < minRank){
					minRank = curRank;
				}
				//new bounds being created fir straight chance
				lowerRank = minRank - (SIZE - 1 - (maxRank - minRank));
				higherRank = maxRank + (SIZE - 1 - (maxRank - minRank));
				
				if(lowerRank < 0){
					lowerRank = 0;
				}
				if(higherRank > 13){
					higherRank = 13;
				}
			}else{//stop if no chance for straight
				score = 0;
				break;
			}
		}
		if (score > 1) {
			score = straightScore * ((float)score / SIZE);
		}else if (score == 1) {
			score = straightScore * ((float)score / SIZE / 2);
		}
		
		return  score;
	}
	
	public void makePlay(Card card, int row, int col) {
		// match simDeck to event
		int cardIndex = numPlays;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[numPlays];
		simDeck[numPlays] = card;
		
		// update plays to reflect chosen play in sequence
		grid[row][col] = card;
		int play = row * SIZE + col;
		int j = 0;
		while (plays[j] != play){
			j++;
		}
		plays[j] = plays[numPlays];
		plays[numPlays] = play;
		
		// increment the number of plays taken
		numPlays++;
	}
	
	public void undoPlay() { // undo the previous play
		numPlays--;
		int play = plays[numPlays];
		grid[play / SIZE][play % SIZE] = null;
	}
	
	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#getName()
	 */
	@Override
	public String getName() {
		return "EWouldsPlayer";
	}
	
	/**
	 * Demonstrate EWouldsPlayer with British point system.
	 * @param args (not used)
	 */
	public static void main(String[] args) {
		PokerSquaresPointSystem system = PokerSquaresPointSystem.getBritishPointSystem();
		System.out.println(system);
		new PokerSquares(new EWouldsPlayer(), system).play(); // play a single game
	}
}
