package pa.arrau.rst;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is the main class used to construct the ARRAU PA file.
 */
public class ArrauPAConstructor {
	
	/**
	 * Needs three arguments, first the path to the RST subpart of the ARRAU corpus (e.g. res/ARRAU/data/RST_DTreeBank/),
	 * second the corpus type used to build the ARRAU PA corpus (train, dev, test), and third the path to the
	 * output directory (e.g. res/ARRAU/output/).
	 */
	public static void main( String[] args ) {
		getPropositionalAnaphoras(args[0], args[1], args[2], true);
	}
	
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
    
    public static class Triple<T, U, V> {
		public final T _1;
		public final U _2;
		public final V _3;
		
		public Triple(T arg1, U arg2, V arg3) {
			super();
		    this._1 = arg1;
		    this._2 = arg2;
		    this._3 = arg3;
		}
	}
	
	/**
	 * This method gets a word map so that we can simply query a word by its ID for any given document.
	 */
	public static Map<Integer, String> getWordMap( String path_to_RST_subpart, String corpusType, String FILENAME ){
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
	
	/**
	 * This method gets a List of sentence spans.
	 */
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
	
	/**
	 * This method gets a List of up to three previous sentences given a sentence index to start from,
	 * the List of all sentence spans, and the word map.
	 */
	public static List<String> getPreviousSentences( int sentIndex, List<String> sentenceSpans, Map<Integer, String> idToWord ){
		List<String> prevSentences = new ArrayList<String>();
		for( int previousSentIndex = sentIndex - 1; previousSentIndex >= 0; previousSentIndex-- ){
			int distanceToAnaph = sentIndex - previousSentIndex;
			if( distanceToAnaph == 5 ){
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
	
	/**
	 * Given the head of the anaphor (as determined by the CollinsHeadFinder in @getPropositionalAnaphoras),
	 * returns the anaphoric function, pronominal or nominal. 
	 */
	public static String getAnaphoricFunction( String anaphorHead ){
		String result = "";
		if( anaphorHead.toLowerCase().equals("this") || anaphorHead.toLowerCase().equals("that") || anaphorHead.toLowerCase().equals("it") ){
			result = "pronominal";
		}
		else{
			result = "nominal";
		}
		return result;
	}
	
	/**
	 * Sub-method for writing an instance to file. This is used when no context between
	 * anaphoric sentence and its antecedent is needed for the output file.
	 */
	public static void writeToJSONFile( BufferedWriter bw, Path filePath, StringBuilder sbAnaphor, String anaphsentence, 
			String unmodifiedanaphsentence, List<String> antecedentstrings, List<Triple<List<String>, List<String>, String>> antecedentcandidates,
			List<String> prevSentences, String anaphFunction, String headOfAnaphor, String type, List<Integer> antecSentIDs, int anaphSentIndex, List<String> antecedentsentences){
		writeToJSONFile( bw, filePath, sbAnaphor, anaphsentence, unmodifiedanaphsentence, antecedentstrings, antecedentcandidates,
				prevSentences, anaphFunction, headOfAnaphor, type, antecSentIDs, anaphSentIndex, antecedentsentences);
	}
	
	/**
	 * Method for writing one instance of a propositional anaphora with all its information to file.
	 */
	public static void writeToJSONFile( BufferedWriter bw, Path filePath, StringBuilder sbAnaphor, String anaphsentence, 
			String unmodifiedanaphsentence, List<String> antecedentstrings, List<Triple<List<String>, List<String>, String>> antecedentcandidates,
			List<String> prevSentences, String anaphFunction, String headOfAnaphor, String type, List<Integer> antecSentIDs, int anaphSentIndex,
			 List<String> context, List<String> antecedentsentences){
		JSONObject obj = new JSONObject();
		obj.put("path_to_doc", filePath.toString().substring(filePath.toString().indexOf("/") + 1, filePath.toString().length()));
		obj.put("anaphor_phrase", sbAnaphor.toString());
	    obj.put("anaphor_sentence", anaphsentence);
	    obj.put("anaphor_function", anaphFunction);
	    obj.put("anaphor_head", headOfAnaphor);
	    obj.put("anaphor_category", type);
	    obj.put("prev_context", prevSentences);

	    JSONArray antecedentList = new JSONArray();
	    for( String s : antecedentstrings ){
	    	antecedentList.add(s);
	    }
	    obj.put("antecedents", antecedentList);

	    obj.put("antec_sentences", antecedentsentences);

	    JSONArray antecedentDistanceList = new JSONArray();
	    for( int i : antecSentIDs ){
	    	int distance = anaphSentIndex - i;
	    	antecedentDistanceList.add(distance);
	    }
	    obj.put("antecedent_distances", antecedentDistanceList);
	        
	    JSONArray antecedentSentenceCandidateList = new JSONArray();
	    JSONArray antecedentSentenceTagList = new JSONArray();
	    JSONArray antecedentTags = new JSONArray();
	    for( Triple<List<String>, List<String>, String> triple : antecedentcandidates ){
	    	for( String s : triple._1 ){
	    		antecedentSentenceCandidateList.add(s);
	    	}
	        for( String s2 : triple._2 ){
	        	antecedentSentenceTagList.add(s2);
	        }
	        antecedentTags.add(triple._3);
	    }
	        
	    obj.put("candidates", antecedentSentenceCandidateList);
	    obj.put("candidates_nodes", antecedentSentenceTagList);
	    
	    Triple<List<String>, List<String>, String> anaphorSentenceCandidates = getCandidatesFromPrevious(unmodifiedanaphsentence, antecedentstrings);

		JSONArray anaphorSentenceCandidateList = new JSONArray();
	    JSONArray anaphorSentenceTagList = new JSONArray();
	    for( String s : anaphorSentenceCandidates._1 ){
	    	anaphorSentenceCandidateList.add(s);
	    }
	    for( String s2 : anaphorSentenceCandidates._2 ){
	    	anaphorSentenceTagList.add(s2);
	    }
	    obj.put("candidates_0", anaphorSentenceCandidateList);
	    obj.put("candidates_nodes_0", anaphorSentenceTagList);
	        	
		for( int preIndex = 0; preIndex < prevSentences.size(); preIndex++ ){
			String preSentence = prevSentences.get(preIndex);
			Triple<List<String>, List<String>, String> preCandi = getCandidatesFromPrevious(preSentence, antecedentstrings);
				
			int number = preIndex + 1;
				
			JSONArray previousSentenceCandidateList = new JSONArray();
			JSONArray previousSentenceTagList = new JSONArray();
		    for( String s : preCandi._1 ){
		    	previousSentenceCandidateList.add(s);
		    }
		    for( String s2 : preCandi._2 ){
		    	previousSentenceTagList.add(s2);
		    }
		        
		    obj.put("candidates_" + number, previousSentenceCandidateList);
		    obj.put("candidates_nodes_" + number, previousSentenceTagList);
		}
			
		obj.put("antecedent_nodes", antecedentTags);
		if( context != null ){
			obj.put("intervening_context", context);
		}

		try{
			bw.write(obj.toJSONString());
			bw.write(",");
		    bw.newLine();
		}
		catch( IOException e ){
			e.printStackTrace();
		}
	}
	
	/**
	 * Takes four arguments, first the path to the RST subpart of the ARRAU corpus (e.g. res/ARRAU/data/RST_DTreeBank/),
	 * second the corpus type used to build the ARRAU PA corpus (train, dev, test), third the path to the
	 * output directory (e.g. res/ARRAU/output/), and lastly a boolean flag indicating whether intervening context
	 * between an anaphoric sentence and its antecedent sentence is needed or not.
	 */
	public static void getPropositionalAnaphoras( String path_to_RST_subpart, String corpusType, String path_to_output_dir, boolean context ) {
		try{
			List<String> anaphors = new ArrayList<String>();
			BufferedWriter bw;
			if( context == true ){
				bw = new BufferedWriter(new FileWriter(path_to_output_dir + "artificial-" + corpusType + "-data_with_context.json") );
			}
			else{
				bw = new BufferedWriter(new FileWriter(path_to_output_dir + "artificial-" + corpusType + "-data.json") );
			}
			bw.write("[");
	        bw.newLine();
			
			Files.walk(Paths.get(path_to_RST_subpart + corpusType + "/MMAX/markables")).forEach(filePath -> {
				try {
					// Start by checking FILENAME_coref_level files to get anaphors of interest.
					// This basically gets our anaphors.
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
									// We are only interested in anaphor types indicating propositional anaphors
									// i.e. that are of type abstract or plan.
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
										
										String anaphsentence = "";
										String unmodifiedanaphsentence = "";
										List<String> prevSentences = new ArrayList<String>();
										int anaphSentIndex = 0;
										for( int sentIndex = 0; sentIndex < sentenceSpans.size(); sentIndex++){
											String sentSpan = sentenceSpans.get(sentIndex);
											int sentSpanBegin = Integer.parseInt(sentSpan.split("\\.\\.")[0].split("_")[1]);
											int sentSpanEnd = Integer.parseInt(sentSpan.split("\\.\\.")[1].split("_")[1]);
																
											if( (sentSpanBegin <= anaphbegin) && (anaphbegin <= sentSpanEnd) ){
												anaphsentence = getAnaphSentenceString(sentSpanBegin, sentSpanEnd, anaphorSpanBegin, anaphorSpanEnd, idToWord );
												unmodifiedanaphsentence = getSentenceString(sentSpanBegin, sentSpanEnd, idToWord );
												anaphSentIndex = sentIndex;
												prevSentences = getPreviousSentences( sentIndex, sentenceSpans, idToWord );
												break;
											}
										}
										
										try{
											// Next we get the markable units associated with FILENAME in the FILENAME_unit_level file.
											// This basically gets our antecedents.
											DocumentBuilderFactory dbFactory2 = DocumentBuilderFactory.newInstance();
											DocumentBuilder dBuilder2 = dbFactory2.newDocumentBuilder();
											Document doc2 = dBuilder2.parse( new File( path_to_RST_subpart + corpusType + "/MMAX/markables/" + FILENAME + "_unit_level.xml" ) ) ;
											doc2.getDocumentElement().normalize();
																		
											NodeList nListUnitMarkables = doc2.getElementsByTagName("markable");
	
											List<String> antecedentstrings = new ArrayList<String>();
											List<Integer> antecSentIDs = new ArrayList<Integer>();
											List<String> antecedentsentences = new ArrayList<String>();
											List<Triple<List<String>, List<String>, String>> antecedentcandidates = new ArrayList<Triple<List<String>, List<String>, String>>();
											List<String> interveningContexts = new ArrayList<String>();
											
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
																/*
																 * GET INTERVENING CONTEXT
																 */
																String intervening_context = getInterveningContext(anaphSentIndex, sentIndex, sentenceSpans, idToWord);
																interveningContexts.add(intervening_context);
																break;
															}
														}
														antecedentsentences.add(antecsentence);
															
														Triple<List<String>, List<String>, String> antecCand = getCandidates(antecsentence, sbAntecedent.toString());
														antecedentcandidates.add(antecCand);
													}
												}
											}
											HeadFinder chf = new CollinsHeadFinder();
											String headOfAnaph = parse(sbAnaphor.toString()).headTerminal(chf).toString();
											String anaphFunction = getAnaphoricFunction( headOfAnaph );
											if( context == false ){
												writeToJSONFile( bw, filePath, sbAnaphor, anaphsentence, unmodifiedanaphsentence, antecedentstrings, antecedentcandidates,
													prevSentences, anaphFunction, headOfAnaph, type, antecSentIDs, anaphSentIndex, antecedentsentences);
											}
											else{
												writeToJSONFile( bw, filePath, sbAnaphor, anaphsentence, unmodifiedanaphsentence, antecedentstrings, antecedentcandidates,
														prevSentences, anaphFunction, headOfAnaph, type, antecSentIDs, anaphSentIndex, interveningContexts, antecedentsentences);
											}

											anaphors.add(sbAnaphor.toString());
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
			bw.write("]");
			bw.flush();
			bw.close();
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
	
	/**
	 * This method gets the intervening context between an anaphoric sentence and its antecedent sentence.
	 */
	public static String getInterveningContext( int anaphSentIndex, int antecSentIndex, List<String> sentenceSpans, Map<Integer, String> idToWord ){
		if( anaphSentIndex == antecSentIndex || anaphSentIndex - antecSentIndex == 1){
			return "";
		}
		else{
			StringBuilder sb = new StringBuilder();
			for( int i = antecSentIndex + 1; i < anaphSentIndex; i++ ){
				String sentSpan = sentenceSpans.get(i);
				int sentSpanBegin = Integer.parseInt(sentSpan.split("\\.\\.")[0].split("_")[1]);
				int sentSpanEnd = Integer.parseInt(sentSpan.split("\\.\\.")[1].split("_")[1]);
	
				String interveningsentence = getSentenceString(sentSpanBegin, sentSpanEnd, idToWord );
				sb.append(interveningsentence).append(" ");
			}
			return sb.toString().trim();
		}
	}

	/**
	 * This method gets a String representation of a sentence indicated by its beginning and ending index.
	 */
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
	
	/**
	 * This method gets a String representation of a an anaphoric sentence with the anaphor marked by surrounding -X-.
	 */
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
    
    /**
     * @return	List of candidates without the antecedent, List of candidate tags without the antecedent, Tag of antecedent
     */
	public static Triple<List<String>, List<String>, String> getCandidates( String sentence, String antecedent ) {
		List<String> candidates = new ArrayList<String>();
	    List<String> candidateConstituencyTags = new ArrayList<String>();
	    String antecTag = "";
	    Tree parsedSentence = parse( sentence );
	    Set<Tree> constit = parsedSentence.subTrees();
	    constit.add(parsedSentence);
	        
	    String checkAntecedent = antecedent;
	    checkAntecedent = checkAntecedent.replaceAll("\\.", "");    
	        
	    // For similarity check
	    antecedent = antecedent.replaceAll("--", "");
		antecedent = antecedent.replaceAll("''", "");
		antecedent = antecedent.replaceAll("``", "");
		antecedent = antecedent.replaceAll("\\.", "");
		antecedent = antecedent.replaceAll(",", "");
			
	    for( Tree co : constit ){
	        StringBuilder candidate = new StringBuilder();
	        if( !co.isLeaf() ){
		        List<Tree> coleaves = co.getLeaves();
		        for( Tree leaf : coleaves ) {
		        	candidate.append(leaf.label().value()).append(" ");
		        }
		        	
		        String checkCandidate = candidate.toString();
		        checkCandidate = checkCandidate.replaceAll("\\.", "");
		        if( checkCandidate.toString().trim().matches("\\Q" + checkAntecedent.trim() + "\\E") ){
		        	if( antecTag.isEmpty() || !antecTag.equals("S") || !antecTag.equals("VP") || !antecTag.equals("PP") ){
		        		antecTag = co.label().value();
		        	}
		        	continue;
		        }
		        	
		        String newcandidate = candidate.toString();
		        newcandidate = newcandidate.replaceAll("''", "");
				newcandidate = newcandidate.replaceAll("``", "");
				newcandidate = newcandidate.replaceAll("\\.", "");
				newcandidate = newcandidate.replaceAll(",", "");
				if( newcandidate.contains(antecedent) || antecedent.contains(newcandidate) ){
					int candidateSize = newcandidate.split(" ").length;
					int antecedentSize = antecedent.split(" ").length;
					if( Math.abs(candidateSize - antecedentSize) != 1  ){
						candidates.add(candidate.toString().trim());
			        	candidateConstituencyTags.add(co.label().value());
					}
				}
				else{
					candidates.add(candidate.toString().trim());
		        	candidateConstituencyTags.add(co.label().value());
				}
	        }
	    }
	    
	    if(antecTag.isEmpty()){
	    	edu.stanford.nlp.simple.Document dc = new edu.stanford.nlp.simple.Document(sentence);
	    	if( dc.sentences().size() > 1 ){
	    		antecTag = "S";
	    	}
	    	else{
	    		/* Choose smallest constituent that String.contains the antecedent */
	    		Map<String, String> potentialAntecParentToTag = new HashMap<String, String>();
	    		for( Tree co : constit ){
	    	        StringBuilder candidate = new StringBuilder();
	    	        if( !co.isLeaf() ){
	    		        List<Tree> coleaves = co.getLeaves();
	    		        for( Tree leaf : coleaves ) {
	    		        	candidate.append(leaf.label().value()).append(" ");
	    		        }
	    		        	
	    		        String checkCandidate = candidate.toString();
	    		        checkCandidate = checkCandidate.replaceAll("\\.", "");
	    		        if( checkCandidate.toString().trim().contains(checkAntecedent.trim()) && !co.label().value().equals("ROOT") ){
	    		        	potentialAntecParentToTag.put(checkCandidate, co.label().value());
	    		        }
	    	        }
	    		}
	    		if( !potentialAntecParentToTag.isEmpty() ){
		    		List<String> keys = new ArrayList<String>(potentialAntecParentToTag.keySet());
		    		String smallestConstit = getShortestString(keys);
		    		antecTag = potentialAntecParentToTag.get(smallestConstit);
	    		}
	    		else{
	    			antecTag = "S";
	    		}
	    	}
	    }
	    
	    List<String> finalCandidates = new ArrayList<String>();
	    List<String> finalCandidateConstituencyTags = new ArrayList<String>();
	    Map<String, Map<String, Integer>> map = new HashMap<String, Map<String, Integer>>();	// {duplicate-candidate -> {tag1->#occ, tag2->#occ}}
		Set<String> set1 = new HashSet<String>();
		for( int i = 0; i < candidates.size(); i++ ){
			String cand = candidates.get(i);
			if( !set1.add(cand) ){
				String tag = candidateConstituencyTags.get(i);
				if( !map.containsKey(cand) ){
					int indexOfFirstCandOcc = candidates.indexOf(cand);
					String firstTag = candidateConstituencyTags.get(indexOfFirstCandOcc);
					map.put(cand, new HashMap<String, Integer>());
					map.get(cand).put(firstTag, 1);
				}
				Map<String, Integer> tagOccurences = map.get(cand);
				if( tagOccurences.containsKey(tag) ){
					int oldOccurence = tagOccurences.get(tag);
					tagOccurences.put(tag, oldOccurence + 1);
				}
				else{
					tagOccurences.put(tag, 1);
				}
			}
		}
		if( !map.isEmpty() ){
			for( String key : map.keySet() ){
				Map<String, Integer> tagToOcc = map.get(key);
				
				Map.Entry<String, Integer> maxEntry = null;
				for( Map.Entry<String, Integer> entry : tagToOcc.entrySet() ){
					if( maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0 ){
						maxEntry = entry;
					}
				}
				finalCandidates.add(key);
				finalCandidateConstituencyTags.add(maxEntry.getKey());
			}
		}
		
		for( int t = 0; t < candidates.size(); t++ ){
			String aCandidate = candidates.get(t);
			if( !finalCandidates.contains(aCandidate) ){
				finalCandidates.add(aCandidate);
				finalCandidateConstituencyTags.add(candidateConstituencyTags.get(t));
			}
		}
	    
		Triple<List<String>, List<String>, String> candToConstitTags = new Triple<List<String>, List<String>, String>(finalCandidates, finalCandidateConstituencyTags, antecTag);
		return candToConstitTags;
    }
	
	public static String getShortestString( List<String> keyList ) {
		int maxLength = keyList.get(0).length();
		String shortestString = keyList.get(0);
		for( String s : keyList ){
			if( s.length() < maxLength ){
				maxLength = s.length();
				shortestString = s;
			}
		}
		return shortestString;
	}
    
    /**
     * This method is for getting the candidates and tags of candidates for previous sentences as well as the sentence that contains the anaphor.
     * 
     * @return	List of candidates without the antecedent, List of candidate tags without the antecedent, Tag of antecedent (which is empty here as it is not needed)
     */
    public static Triple<List<String>, List<String>, String> getCandidatesFromPrevious( String sentence, List<String> antecedents ) {
        Tree parsedSentence = parse( sentence );
    	Set<Tree> constit = parsedSentence.subTrees();
        constit.add(parsedSentence);
        
        // For similarity check
        List<String> newantecedents = new ArrayList<String>();
        for( String antecedent : antecedents ){
        	antecedent = antecedent.replaceAll("--", "");
    		antecedent = antecedent.replaceAll("''", "");
    		antecedent = antecedent.replaceAll("``", "");
    		antecedent = antecedent.replaceAll("\\.", "");
    		antecedent = antecedent.replaceAll(",", "");
    		newantecedents.add(antecedent);
        }
        
        List<String> candidates = new ArrayList<String>();
        List<String> candidateConstituencyTags = new ArrayList<String>();
        String antecTag = "";			// REALLY NOT RELEVANT
        for( Tree co : constit ){
        	StringBuilder candidate = new StringBuilder();
        	if( !co.isLeaf() ){
	        	List<Tree> coleaves = co.getLeaves();
	        	for( Tree leaf : coleaves ) {
	        		candidate.append(leaf.label().value()).append(" ");
	            }

	        	if( antecedents.contains(candidate.toString().trim()) ){
	        		continue;
	        	}

	        	String newcandidate = candidate.toString();
	        	newcandidate = newcandidate.replaceAll("''", "");
				newcandidate = newcandidate.replaceAll("``", "");
				newcandidate = newcandidate.replaceAll("\\.", "");
				newcandidate = newcandidate.replaceAll(",", "");
				for( String newAntec : newantecedents ){
					if( newcandidate.contains(newAntec) || newAntec.contains(newcandidate) ){
						int candidateSize = newcandidate.split(" ").length;
						int antecedentSize = newAntec.split(" ").length;
						if( Math.abs(candidateSize - antecedentSize) != 1  ){
							candidates.add(candidate.toString().trim());
			        		candidateConstituencyTags.add(co.label().value());
						}
					}
					else{
						candidates.add(candidate.toString().trim());
		        		candidateConstituencyTags.add(co.label().value());
					}
				}
        	}
        }
        
        List<String> finalCandidates = new ArrayList<String>();
	    List<String> finalCandidateConstituencyTags = new ArrayList<String>();
	    Map<String, Map<String, Integer>> map = new HashMap<String, Map<String, Integer>>();	// {duplicate-candidate -> {tag1->#occ, tag2->#occ}}
		Set<String> set1 = new HashSet<String>();
		for( int i = 0; i < candidates.size(); i++ ){
			String cand = candidates.get(i);
			if( !set1.add(cand) ){
				String tag = candidateConstituencyTags.get(i);
				if( !map.containsKey(cand) ){
					int indexOfFirstCandOcc = candidates.indexOf(cand);
					String firstTag = candidateConstituencyTags.get(indexOfFirstCandOcc);
					map.put(cand, new HashMap<String, Integer>());
					map.get(cand).put(firstTag, 1);
				}
				Map<String, Integer> tagOccurences = map.get(cand);
				if( tagOccurences.containsKey(tag) ){
					int oldOccurence = tagOccurences.get(tag);
					tagOccurences.put(tag, oldOccurence + 1);
				}
				else{
					tagOccurences.put(tag, 1);
				}
			}
		}
		if( !map.isEmpty() ){
			for( String key : map.keySet() ){
				Map<String, Integer> tagToOcc = map.get(key);
				
				Map.Entry<String, Integer> maxEntry = null;
				for( Map.Entry<String, Integer> entry : tagToOcc.entrySet() ){
					if( maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0 ){
						maxEntry = entry;
					}
				}
				finalCandidates.add(key);
				finalCandidateConstituencyTags.add(maxEntry.getKey());
			}
		}
		
		for( int t = 0; t < candidates.size(); t++ ){
			String aCandidate = candidates.get(t);
			if( !finalCandidates.contains(aCandidate) ){
				finalCandidates.add(aCandidate);
				finalCandidateConstituencyTags.add(candidateConstituencyTags.get(t));
			}
		}
	    
		Triple<List<String>, List<String>, String> candToConstitTags = new Triple<List<String>, List<String>, String>(finalCandidates, finalCandidateConstituencyTags, antecTag);
		return candToConstitTags;         
    }
	
}
