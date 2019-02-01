package pa.csn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.nytlabs.corpus.NYTCorpusDocument;
import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.trees.Tree;

public class NYTMappingResolver {

	/**
	 * Needs four arguments:
	 * 1) path to NYT corpus
	 * 2) path to mapping files
	 * 3) path to CSN JSON file
	 * 4) path to output CSN JSON file
	 */
	public static void main(String[] args) {
		resolveMapping(args[0], args[1], args[2], args[3]);
	}

	/**
	 * Given the NYT corpus directory (e.g. res/nyt_corpus/), a directory containing files in the format
	 * "sentence \t|\t	path" (res/nyt_corpus/csn-YYYY-mapping.txt),
	 * given the CSN (res/CSN/csn.json), and given the output file (e.g. res/CSN/csn_context.json),
	 * iterates over CSN, resolves source_sentence to path and gets context from that file.
	 * It then gets candidates and tags for the context and supplements CSN.
	 * 
	 * @param path_to_NYT_corpus
	 * @param path_to_mappings
	 * @param path_to_csn
	 * @param path_to_output_csn
	 */
	public static void resolveMapping( String path_to_NYT_corpus, String path_to_mappings, String path_to_csn, String path_to_output_csn ) {
		try{
			Map<String, List<String>> sentencesToPaths = new HashMap<String, List<String>>();
			Files.walk(Paths.get(path_to_mappings)).forEach(filePath -> {
				try {
					if( Files.isRegularFile(filePath) && !Files.isHidden(filePath) ) {
						FileInputStream fstream = new FileInputStream( filePath.toString() );
						DataInputStream in = new DataInputStream( fstream );
						BufferedReader br = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );

						String line;
						while( ( line = br.readLine() ) != null ){
							String[] sentenceAndPath = line.split("\t|\t");
							if( sentencesToPaths.containsKey(sentenceAndPath[0]) ){
								ArrayList<String> alreadyPaths = (ArrayList) sentencesToPaths.get(sentenceAndPath[0]);
								alreadyPaths.add(sentenceAndPath[2]);
								sentencesToPaths.put(sentenceAndPath[0], alreadyPaths);
							}
							else{
								List<String> path1ToFile = new ArrayList<String>();
								path1ToFile.add(sentenceAndPath[2]);					// file path is at index 2!
								sentencesToPaths.put(sentenceAndPath[0], path1ToFile);
							}
						}
						br.close();
					}
				}
				catch (Exception e){
					e.printStackTrace();
				}
			});
			
			System.out.println("We have to check " + sentencesToPaths.size() + " instances.");
			
			BufferedWriter writer = new BufferedWriter(new FileWriter(path_to_output_csn) );
			writer.write("[");
	        writer.newLine();
			
	        NYTCorpusDocumentParser nytP = new NYTCorpusDocumentParser();
			JSONParser parser = new JSONParser();
			JSONArray a = (JSONArray) parser.parse(new FileReader(path_to_csn));
			for( int i = 0; i < a.size(); i++ ){
				Object o = a.get(i);
				JSONObject instance = (JSONObject) o;
				
				JSONArray antecedents = (JSONArray) instance.get("artificial_antecedent");
				String antecedent = (String) antecedents.get(0);
				
				String sentenceToCheck = (String) instance.get("artificial_source");
				ArrayList<String> tokens = new ArrayList<String>(Arrays.asList(sentenceToCheck.split(" ")));
				String newSentenceToCheck = detokenize(tokens);
				newSentenceToCheck = newSentenceToCheck.replaceAll("--", "");
				newSentenceToCheck = newSentenceToCheck.replaceAll("''", "");
				newSentenceToCheck = newSentenceToCheck.replaceAll("``", "");
				newSentenceToCheck = newSentenceToCheck.replaceAll("  ", " ");
				newSentenceToCheck = newSentenceToCheck.replaceAll(" \\.", ".");
				newSentenceToCheck = newSentenceToCheck.replaceAll(" \\?", "?");
				newSentenceToCheck = newSentenceToCheck.replaceAll("` ", "");
				newSentenceToCheck = newSentenceToCheck.trim();
				
				if( sentencesToPaths.containsKey(sentenceToCheck) ){
					List<String> pathsForThatSentence = sentencesToPaths.get(sentenceToCheck);
					
					String path = pathsForThatSentence.get(0);
					String goodPath = path.substring(path.indexOf("data"), path.length());

					pathsForThatSentence.remove(0);
					if( pathsForThatSentence.isEmpty() ){
						sentencesToPaths.remove(sentenceToCheck);
					}
					
					NYTCorpusDocument test = nytP.parseNYTCorpusDocumentFromFile( new File( path_to_NYT_corpus + goodPath ) , false);
					
					if( test.getBody() != null ){
						Document doc = new Document(test.getBody());
						for( int sindex = 0; sindex < doc.sentences().size(); sindex++ ){
							Sentence sent = doc.sentence(sindex);
							String sentence = String.join(" ", sent.words());
							
							if( sentence.equals( sentenceToCheck ) ){
								// Get previous three sentences
								for( int previousSentIndex = sindex - 1; previousSentIndex >= 0; previousSentIndex-- ){
									int distanceToAnaph = sindex - previousSentIndex;
									if( distanceToAnaph == 4 ){
										break;
									}
									String prevSentence = doc.sentence(previousSentIndex).toString();
									Tuple<List<String>, List<String>> preCandi = getCandidates(prevSentence, antecedent);

									JSONArray previousSentenceCandidateList = new JSONArray();
	    					        JSONArray previousSentenceTagList = new JSONArray();
	    					        for( String s : preCandi._1 ){
	    					        	previousSentenceCandidateList.add(s);
	        					    }
	    					        for( String s2 : preCandi._2 ){
	    					        	previousSentenceTagList.add(s2);
	        					    }
	    					        
	    					        instance.put("candidates_" + distanceToAnaph + "_minus_all_antecedents", previousSentenceCandidateList);
	    					        instance.put("candidates_nodes_" + distanceToAnaph + "_minus_all_antecedents", previousSentenceTagList);
								}
								instance.put("path_to_doc", "nyt_corpus/" + goodPath);
								break;
							}
						}
					}
				}
				else if( sentencesToPaths.containsKey(newSentenceToCheck) ){
					List<String> pathsForThatSentence = sentencesToPaths.get(newSentenceToCheck);
					
					String path = pathsForThatSentence.get(0);
					String goodPath = path.substring(path.indexOf("data"), path.length());

					pathsForThatSentence.remove(0);
					if( pathsForThatSentence.isEmpty() ){
						sentencesToPaths.remove(newSentenceToCheck);
					}
					
					NYTCorpusDocument test = nytP.parseNYTCorpusDocumentFromFile( new File( path_to_NYT_corpus + goodPath ) , false);
					
					if( test.getBody() != null ){
						Document doc = new Document(test.getBody());
						for( int sindex = 0; sindex < doc.sentences().size(); sindex++ ){
							Sentence sent = doc.sentence(sindex);
							String sentence = sent.toString();
							
							sentence = sentence.replaceAll("--", "");
							sentence = sentence.replaceAll("''", "");
							sentence = sentence.replaceAll("``", "");
							
							if( sentence.equals( newSentenceToCheck ) ){
								// Get previous three sentences
								for( int previousSentIndex = sindex - 1; previousSentIndex >= 0; previousSentIndex-- ){
									int distanceToAnaph = sindex - previousSentIndex;
									if( distanceToAnaph == 4 ){
										break;
									}
									String prevSentence = doc.sentence(previousSentIndex).toString();
									Tuple<List<String>, List<String>> preCandi = getCandidates(prevSentence, antecedent);

									JSONArray previousSentenceCandidateList = new JSONArray();
	    					        JSONArray previousSentenceTagList = new JSONArray();
	    					        for( String s : preCandi._1 ){
	    					        	previousSentenceCandidateList.add(s);
	        					    }
	    					        for( String s2 : preCandi._2 ){
	    					        	previousSentenceTagList.add(s2);
	        					    }
	    					        
	    					        instance.put("candidates_" + distanceToAnaph + "_minus_all_antecedents", previousSentenceCandidateList);
	    					        instance.put("candidates_nodes_" + distanceToAnaph + "_minus_all_antecedents", previousSentenceTagList);
								}
								instance.put("path_to_doc", "nyt_corpus/" + goodPath);
								break;
							}
						}
					}
				}
				writer.write(instance.toJSONString());
				if( i != a.size() - 1 ){
					writer.write(",");
				}
		        writer.newLine();
		        writer.flush();
			}
			writer.write("]");
			writer.flush();
			writer.close();
			
			System.out.println("Done!");
			System.out.println("There remain " + sentencesToPaths.size() + " entries.");
		}
		catch( Exception e ){
			e.printStackTrace();
		}

	}
	
	public static String detokenize( ArrayList<String> tokens ){
        //Define list of punctuation characters that should NOT have spaces before or after 
		List<String> noSpaceBefore = new LinkedList<String>(Arrays.asList(",", ".",";", ":", ")", "}", "]", "?", "!", "'s", "'", "n't", "'ll", "'re", "'m", "'ve"));
        List<String> noSpaceAfter = new LinkedList<String>(Arrays.asList("(", "[","{", "\"","", "$"));

        StringBuilder sentence = new StringBuilder();
        
        tokens.add(0, "");  //Add an empty token at the beginning because loop checks as position-1 and "" is in noSpaceAfter
        for (int i = 1; i < tokens.size(); i++) {
            if (noSpaceBefore.contains(tokens.get(i))
                    || noSpaceAfter.contains(tokens.get(i - 1))) {
                sentence.append(tokens.get(i));
            } else {
                sentence.append(" " + tokens.get(i));
            }

            if ("\"".equals(tokens.get(i - 1))) {
                if (noSpaceAfter.contains("\"")) {
                    noSpaceAfter.remove("\"");
                    noSpaceBefore.add("\"");
                } else {
                    noSpaceAfter.add("\"");
                    noSpaceBefore.remove("\"");
                }
            }
        }
        return sentence.toString();
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
	
	/**
	 * Takes a parsed sentence and antecedent as input and outputs a tuple of List of candidates without antecedent and List of candidate tags without antecedent.
	 * 
	 * @param parsedSentence
	 * @param antecedent
	 * @return
	 */
	public static Tuple<List<String>, List<String>> getCandidates( String sentence, String antecedent ) {
		Tree parsedSentence = parse( sentence );
		Set<Tree> constit = parsedSentence.subTrees();
        constit.add(parsedSentence);
        
        String originalAntecedent = antecedent;
        
        // For similarity check
        antecedent = antecedent.replaceAll("--", "");
		antecedent = antecedent.replaceAll("''", "");
		antecedent = antecedent.replaceAll("``", "");
		antecedent = antecedent.replaceAll("\\.", "");
		antecedent = antecedent.replaceAll(",", "");
        
        List<String> candidates = new ArrayList<String>();
        List<String> candidateConstituencyTags = new ArrayList<String>();
        for( Tree co : constit ){
        	StringBuilder candidate = new StringBuilder();
        	if( !co.isLeaf() ){
	        	List<Tree> coleaves = co.getLeaves();
	        	for( Tree leaf : coleaves ) {
	        		candidate.append(leaf.label().value()).append(" ");
	            }
	        	if( candidate.toString().trim().matches("\\Q" + originalAntecedent + "\\E") ){
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
        Tuple<List<String>, List<String>> candToConstitTags = new Tuple<List<String>, List<String>>(candidates, candidateConstituencyTags);
        return candToConstitTags;            
    }
	
	public static class Tuple<T, U> {
		public final T _1;
		public final U _2;
		
		public Tuple(T arg1, U arg2) {
			super();
		    this._1 = arg1;
		    this._2 = arg2;
		}
	}
}
