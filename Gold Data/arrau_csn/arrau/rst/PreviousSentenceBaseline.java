package pa.arrau.rst;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.StringReader;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class implements a baseline model for the ARRAU data set that assumes that the previous
 * sentence of an anaphor-containing sentence is its antecedent.
 */
public class PreviousSentenceBaseline {
	
	/**
	 * Takes two arguments, first the path to the RST subpart of the ARRAU corpus (e.g. res/ARRAU/data/RST_DTreeBank/)
	 * and second the corpus type to evaluate (train, dev, test).
	 *
	 * @param args
	 */
	public static void main( String[] args ) {
		runBaseline(args[0], args[1]);
	}
	
	public static int resultAll, resultD1, resultD2, resultD3, resultAllNominal, resultAllPronominal, resultNominalD1, resultNominalD2, resultNominalD3,
		resultPronominalD1, resultPronominalD2, resultPronominalD3;
	
	public static int numberD1, numberD2, numberD3, numberNominal, numberPronominal, numberNominalD1, numberNominalD2, numberNominalD3,
		numberPronominalD1, numberPronominalD2, numberPronominalD3;
	
	public final static String PCG_MODEL = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";        

    public static final TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "invertible=true");

    public static final LexicalizedParser stanfordparser = LexicalizedParser.loadModel(PCG_MODEL);

    public static Tree parse( String str ) {                
        List<CoreLabel> tokens = tokenize(str);
        Tree tree = stanfordparser.apply(tokens);
        return tree;
    }

    public static List<CoreLabel> tokenize( String str ) {
        Tokenizer<CoreLabel> tokenizer = tokenizerFactory.getTokenizer(new StringReader(str));    
        return tokenizer.tokenize();
    }
	
	public static Map<Integer, String> getWordMap( String path_to_RST_subpart, String corpusType, String FILENAME ){
		/* This is a very important map as we can simply query a word by its ID */
		Map<Integer, String> idToWord = new HashMap<Integer, String>();
		try{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse( new File( path_to_RST_subpart + corpusType + "/MMAX/Basedata/" + FILENAME + "_words.xml" ) ) ;
			doc.getDocumentElement().normalize();
							
			NodeList nListWords = doc.getElementsByTagName("word");
			
			for( int ittemp = 0; ittemp < nListWords.getLength(); ittemp++) {
				Node nWordNode = nListWords.item(ittemp);
				idToWord.put(ittemp + 1, nWordNode.getTextContent());
			}
		}
		catch( Exception e ){
			e.printStackTrace();
		}
		return idToWord;
	}
	
	public static List<String> getSentenceSpans( String path_to_RST_subpart, String corpusType, String FILENAME ){
		List<String> sentenceSpans = new ArrayList<String>();
		try{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse( new File( path_to_RST_subpart + corpusType + "/MMAX/markables/" + FILENAME + "_sentence_level.xml" ) ) ;
			doc.getDocumentElement().normalize();
							
			NodeList nListSentences = doc.getElementsByTagName("markable");
							
			for( int ittemp = 0; ittemp < nListSentences.getLength(); ittemp++) {
				Node nSentenceNode = nListSentences.item(ittemp);
				Element eSentenceElement = (Element) nSentenceNode;
				sentenceSpans.add(eSentenceElement.getAttribute("span"));
			}
		}
		catch( Exception e ){
			e.printStackTrace();
		}
		return sentenceSpans;
	}
	
	public static String getSentenceString( int sentSpanBegin, int sentSpanEnd, Map<Integer, String> idToWord ){
		StringBuilder sbSentence = new StringBuilder();
		for( int index = sentSpanBegin; index < sentSpanEnd + 1; index++ ){
			sbSentence.append(idToWord.get(index));
			if( index != sentSpanEnd ){
				sbSentence.append(" ");
			}
		}
		return sbSentence.toString();
	}
	
	public static String getAnaphSentenceString( int sentSpanBegin, int sentSpanEnd, String anaphorSpanBegin, String anaphorSpanEnd, Map<Integer, String> idToWord ){
		int anaphbegin = Integer.parseInt(anaphorSpanBegin.split("_")[1]);
		boolean multiword = false;
		if( !anaphorSpanEnd.isEmpty() ){
			multiword = true;
		}
		StringBuilder sbSentence = new StringBuilder();
		for( int index = sentSpanBegin; index < sentSpanEnd + 1; index++ ){
			String wordAtIndex = idToWord.get(index);
			if( index == anaphbegin && !multiword ){
				wordAtIndex = "-X-" + wordAtIndex + "-X-";
			}
			else if( index == anaphbegin && multiword ){
				wordAtIndex = "-X-" + wordAtIndex;
			}
			else if( multiword ){
				int anaphend = Integer.parseInt(anaphorSpanEnd.split("_")[1]);
				if( index == anaphend ){
					wordAtIndex = wordAtIndex + "-X-";
				}
			}
			sbSentence.append(wordAtIndex);
			if( index != sentSpanEnd ){
				sbSentence.append(" ");
			}
		}
		return sbSentence.toString();
	}
	
	public static String getAnaphoricFunction( String anaphorHead ){
		String result = "";
		if( anaphorHead.equals("this") || anaphorHead.equals("that") || anaphorHead.equals("it") ||
				anaphorHead.equals("This") || anaphorHead.equals("That") || anaphorHead.equals("It")){
			result = "pronominal";
		}
		else{
			result = "nominal";
		}
		return result;
	}
	
	public static List<String> getPreviousSentences( int sentIndex, List<String> sentenceSpans, Map<Integer, String> idToWord ){
		List<String> prevSentences = new ArrayList<String>();
		for( int previousSentIndex = sentIndex - 1; previousSentIndex >= 0; previousSentIndex-- ){
			int distanceToAnaph = sentIndex - previousSentIndex;
			if( distanceToAnaph == 4 ){
				break;
			}
			String previousSentSpan = sentenceSpans.get(previousSentIndex);
			int previousSentSpanBegin = Integer.parseInt(previousSentSpan.split("\\.\\.")[0].split("_")[1]);
			int previousSentSpanEnd = Integer.parseInt(previousSentSpan.split("\\.\\.")[1].split("_")[1]);
			String previoussentence = getSentenceString(previousSentSpanBegin, previousSentSpanEnd, idToWord );
			prevSentences.add(previoussentence);
		}
		return prevSentences;
	}
	
	public static void runBaseline( String path_to_RST_subpart, String corpusType ) {
		try{
			List<String> anaphors = new ArrayList<String>();
	        resultAll = 0;
	        resultD1 = 0;
	        resultD2 = 0;
	        resultD3 = 0;
	        resultAllNominal = 0;
	        resultAllPronominal = 0;
	        resultNominalD1 = 0;
	        resultNominalD2 = 0;
	        resultNominalD3 = 0;
	        resultPronominalD1 = 0;
	        resultPronominalD2 = 0;
	        resultPronominalD3 = 0;
	        
	        numberD1 = 0;
	        numberD2 = 0;
	        numberD3 = 0;
	        numberNominal = 0;
	        numberPronominal = 0;
	        numberNominalD1 = 0;
	        numberNominalD2 = 0;
	        numberNominalD3 = 0;
	        numberPronominalD1 = 0;
	        numberPronominalD2 = 0;
	        numberPronominalD3 = 0;
	        
			Files.walk(Paths.get(path_to_RST_subpart + corpusType + "/MMAX/markables")).forEach(filePath -> {
				try {
					if(Files.isRegularFile(filePath) && !Files.isHidden(filePath) && filePath.toString().contains("coref_level") ) {
						try{
							DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
							DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
							Document doc = dBuilder.parse( new File( filePath.toString() ) ) ;
							doc.getDocumentElement().normalize();		
										
							NodeList nListCorefMarkables = doc.getElementsByTagName("markable");

							for (int temp = 0; temp < nListCorefMarkables.getLength(); temp++) {
								Node nCorefNode = nListCorefMarkables.item(temp);
								Element eCorefElement = (Element) nCorefNode;
									
								if( eCorefElement.hasAttribute("segment_antecedent") ){
									String type = eCorefElement.getAttribute("category");
									if( type.equals("abstract") || type.equals("plan") ){
										String anaphorSpan = eCorefElement.getAttribute("span");
										String anaphorSpanBegin = anaphorSpan;
										String anaphorSpanEnd = "";
										if( anaphorSpan.contains("..") ){
											anaphorSpanBegin = anaphorSpan.split("\\.\\.")[0];
											anaphorSpanEnd = anaphorSpan.split("\\.\\.")[1];
										}
												
										List<String> antecedents = new ArrayList<String>();
										String possAntecdents = eCorefElement.getAttribute("segment_antecedent");
											
										// This is a fail-safe in case there is some non-unit annotation
										if( !possAntecdents.startsWith("unit") ){
											break;
										}
										
										// Handles "unit:markable_389;unit:markable_397"
										if( possAntecdents.contains(";") ){
											String[] antecdentArray = possAntecdents.split(";");
											for( String antInArray : antecdentArray ){
												antecedents.add(antInArray.split(":")[1]);
											}
										}
										// Handles "unit:markable_389"
										else{
											antecedents.add(possAntecdents.split(":")[1]);
										}
										
										// Get the clean file name
										String FILENAME = filePath.toString().substring(filePath.toString().lastIndexOf("/") + 1, filePath.toString().length());
										int indexOfSecondUnderscore = FILENAME.indexOf("_", FILENAME.indexOf("_") + 1);
										FILENAME = FILENAME.substring(0, indexOfSecondUnderscore);
											
										// Get the map of word ids to words
										Map<Integer, String> idToWord = getWordMap( path_to_RST_subpart, corpusType, FILENAME );
															
										StringBuilder sbAnaphor = new StringBuilder();
										int anaphbegin = Integer.parseInt(anaphorSpanBegin.split("_")[1]);
										sbAnaphor.append(idToWord.get(anaphbegin));
															
										if(!anaphorSpanEnd.isEmpty()){
											for( int spanBeginIndex = Integer.parseInt(anaphorSpanBegin.split("_")[1]) + 1; spanBeginIndex < Integer.parseInt(anaphorSpanEnd.split("_")[1]) + 1; spanBeginIndex++ ){
												sbAnaphor.append(" ").append(idToWord.get(spanBeginIndex));
											}
										}
										
										// Get list of sentence spans
										List<String> sentenceSpans = getSentenceSpans( path_to_RST_subpart, corpusType, FILENAME );
										
										List<String> prevSentences = new ArrayList<String>();
										int anaphSentIndex = 0;
										for( int sentIndex = 0; sentIndex < sentenceSpans.size(); sentIndex++){
											String sentSpan = sentenceSpans.get(sentIndex);
											int sentSpanBegin = Integer.parseInt(sentSpan.split("\\.\\.")[0].split("_")[1]);
											int sentSpanEnd = Integer.parseInt(sentSpan.split("\\.\\.")[1].split("_")[1]);
																
											if( (sentSpanBegin <= anaphbegin) && (anaphbegin <= sentSpanEnd) ){
												anaphSentIndex = sentIndex;
												prevSentences = getPreviousSentences( sentIndex, sentenceSpans, idToWord );
												break;
											}
										}
										
										try{
											DocumentBuilderFactory dbFactory2 = DocumentBuilderFactory.newInstance();
											DocumentBuilder dBuilder2 = dbFactory2.newDocumentBuilder();
											Document doc2 = dBuilder2.parse( new File( path_to_RST_subpart + corpusType + "/MMAX/markables/" + FILENAME + "_unit_level.xml" ) ) ;
											doc2.getDocumentElement().normalize();
																		
											NodeList nListUnitMarkables = doc2.getElementsByTagName("markable");
	
											List<String> antecedentstrings = new ArrayList<String>();
											List<Integer> antecSentIDs = new ArrayList<Integer>();
											List<String> antecedentsentences = new ArrayList<String>();
					
											for (int temp2 = 0; temp2 < nListUnitMarkables.getLength(); temp2++) {
												Node nUnitNode = nListUnitMarkables.item(temp2);
												Element eUnitElement = (Element) nUnitNode;
														
												for( String correctAntecedent : antecedents ){
													if( eUnitElement.getAttribute("id").equals(correctAntecedent) ){
														String antecedentSpan = eUnitElement.getAttribute("span");
														String antecedentSpanBegin = antecedentSpan;
														String antecedentSpanEnd = "";
																	
														if( antecedentSpan.contains("..") ){
															antecedentSpanBegin = antecedentSpan.split("\\.\\.")[0];
															antecedentSpanEnd = antecedentSpan.split("\\.\\.")[1];
														}
																			
														StringBuilder sbAntecedent = new StringBuilder();
														int antecbegin = Integer.parseInt(antecedentSpanBegin.split("_")[1]);
														sbAntecedent.append(idToWord.get(antecbegin));
																					
														if(!antecedentSpanEnd.isEmpty()){
															for( int antecedentSpanBeginIndex = Integer.parseInt(antecedentSpanBegin.split("_")[1]) + 1; antecedentSpanBeginIndex < Integer.parseInt(antecedentSpanEnd.split("_")[1]) + 1; antecedentSpanBeginIndex++ ){
																sbAntecedent.append(" ").append(idToWord.get(antecedentSpanBeginIndex));
															}
														}		
														antecedentstrings.add(sbAntecedent.toString());
														
														String antecsentence = "";
														for( int sentIndex = 0; sentIndex < sentenceSpans.size(); sentIndex++){
															String sentSpan = sentenceSpans.get(sentIndex);
															int sentSpanBegin = Integer.parseInt(sentSpan.split("\\.\\.")[0].split("_")[1]);
															int sentSpanEnd = Integer.parseInt(sentSpan.split("\\.\\.")[1].split("_")[1]);
			
															if( (sentSpanBegin <= antecbegin) && (antecbegin <= sentSpanEnd) ){
																antecsentence = getSentenceString(sentSpanBegin, sentSpanEnd, idToWord );
																antecSentIDs.add(sentIndex);
																break;
															}
														}
														antecedentsentences.add(antecsentence);
													}
												}
											}
											anaphors.add(sbAnaphor.toString());

											List<Integer> antecedentDistances = new ArrayList<Integer>();
											for( int id : antecSentIDs ){
										    	int distance = anaphSentIndex - id;
										    	antecedentDistances.add(distance);
										    }
																								
											boolean goodInstance = false;
											int minDistance = Collections.min(antecedentDistances);
											if( minDistance <= 1 ){
												numberD1++;
												numberD2++;
												numberD3++;
												goodInstance = true;
											}
											else if( minDistance == 2 ){
												numberD2++;
												numberD3++;
												goodInstance = true;
											}
											else if( minDistance == 3 ){
												numberD3++;
												goodInstance = true;
											}
											if( goodInstance && prevSentences.size() != 0 ){
												String preSentence = prevSentences.get(0);

												for( String s : antecedentstrings ){
													if( s.equals(preSentence) ){
														resultAll++;
														if( minDistance <= 1 ){
															resultD1++;
															resultD2++;
															resultD3++;
														}
														else if( minDistance == 2 ){
															resultD2++;
															resultD3++;
														}
														else if( minDistance == 3 ){
															resultD3++;
														}
														break;
													}
												}
											}
											goodInstance = false;
												
											HeadFinder chf = new CollinsHeadFinder();
											String headOfAnaph = parse(sbAnaphor.toString()).headTerminal(chf).toString();
											String anaphFunction = getAnaphoricFunction( headOfAnaph );
												
											if( anaphFunction.equals("nominal") ){
												numberNominal++;
												
												if( minDistance <= 1 ){
													numberNominalD1++;
													numberNominalD2++;
													numberNominalD3++;
													goodInstance = true;
												}
												else if( minDistance == 2 ){
													numberNominalD2++;
													numberNominalD3++;
													goodInstance = true;
												}
												else if( minDistance == 3 ){
													numberNominalD3++;
													goodInstance = true;
												}
												if( goodInstance && prevSentences.size() != 0){
													String preSentence = prevSentences.get(0);

													for( int i = 0; i < antecedentstrings.size(); i++ ){
														String s = antecedentstrings.get(i);

														if( s.equals(preSentence) ){
															resultAllNominal++;
															if( minDistance <= 1 ){
																resultNominalD1++;
																resultNominalD2++;
																resultNominalD3++;
															}
															else if( minDistance == 2 ){
																resultNominalD2++;
																resultNominalD3++;
															}
															else if( minDistance == 3 ){
																resultNominalD3++;
															}
															break;

														}
													}
												}	
											}
											else{
												numberPronominal++;
												
												if( minDistance <= 1 ){
													numberPronominalD1++;
													numberPronominalD2++;
													numberPronominalD3++;
													goodInstance = true;
												}
												else if( minDistance == 2 ){
													numberPronominalD2++;
													numberPronominalD3++;
													goodInstance = true;
												}
												else if( minDistance == 3 ){
													numberPronominalD3++;
													goodInstance = true;
												}
												if( goodInstance && prevSentences.size() != 0){
													String preSentence = prevSentences.get(0);

													for( int i = 0; i < antecedentstrings.size(); i++ ){
														String s = antecedentstrings.get(i);

														if( s.equals(preSentence) ){
															resultAllPronominal++;
															if( minDistance <= 1 ){
																resultPronominalD1++;
																resultPronominalD2++;
																resultPronominalD3++;
															}
															else if( minDistance == 2 ){
																resultPronominalD2++;
																resultPronominalD3++;
															}
															else if( minDistance == 3 ){
																resultPronominalD3++;
															}
															break;

														}
													}
												}
											}
										}
										catch( Exception e ){
											e.printStackTrace();
										}
									}
								}
							}	
						}
						catch( Exception e ){
							e.printStackTrace();
						}
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			});
			
			System.out.println(anaphors.size() + " instances in " + corpusType);
			System.out.println("For " + resultAll + " / " + anaphors.size() + " instances, the previous sentence is the true antecedent.");
			System.out.println("For " + resultD1 + " / " + numberD1 + " instances for which at least one of the true antecedents was in d<=1, the previous sentence is the true antecedent.");
			System.out.println("For " + resultD2 + " / " + numberD2 + " instances for which at least one of the true antecedents was in d<=2, the previous sentence is the true antecedent.");
			System.out.println("For " + resultD3 + " / " + numberD3 + " instances for which at least one of the true antecedents was in d<=3, the previous sentence is the true antecedent.");
			System.out.println();
			System.out.println("For " + resultAllNominal + " / " + numberNominal + " nominal instances, the previous sentence is the true antecedent.");
			System.out.println("For " + resultNominalD1 + " / " + numberNominalD1 + " nominal instances for which at least one of the true antecedents was in d<=1, the previous sentence is the true antecedent.");
			System.out.println("For " + resultNominalD2 + " / " + numberNominalD2 + " nominal instances for which at least one of the true antecedents was in d<=2, the previous sentence is the true antecedent.");
			System.out.println("For " + resultNominalD3 + " / " + numberNominalD3 + " nominal instances for which at least one of the true antecedents was in d<=3, the previous sentence is the true antecedent.");
			System.out.println();
			System.out.println("For " + resultAllPronominal + " / " + numberPronominal + " pronominal instances, the previous sentence is the true antecedent.");
			System.out.println("For " + resultPronominalD1 + " / " + numberPronominalD1 + " pronominal instances for which at least one of the true antecedents was in d<=1, the previous sentence is the true antecedent.");
			System.out.println("For " + resultPronominalD2 + " / " + numberPronominalD2 + " pronominal instances for which at least one of the true antecedents was in d<=2, the previous sentence is the true antecedent.");
			System.out.println("For " + resultPronominalD3 + " / " + numberPronominalD3 + " pronominal instances for which at least one of the true antecedents was in d<=3, the previous sentence is the true antecedent.");
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
}
