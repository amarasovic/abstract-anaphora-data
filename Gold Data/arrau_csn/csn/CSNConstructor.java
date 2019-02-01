package pa.csn;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.Set;

import edu.stanford.nlp.simple.*;
import edu.stanford.nlp.trees.*;

import org.json.simple.*;

/**
 * This class is used to create an initial JSON file for the CSN data set based on the data provided by Kolhatkar.
 * This data is split according to shell nound (DECISION, FACT, ISSUE, POSSIBILITY, QUESTION, REASON) and is formatted as follows:
 *
 * SENTENCE: <complete sentence>
 * CONTENT: <antecedent of shell noun>
 */
public class CSNConstructor {
	
	/**
	 * Needs two arguments, first the path to the data by Kolhatkar (e.g. res/CSN_Varada/), second the path
	 * to an output file (e.g. res/CSN/csn.json)
	 */
	public static void main(String[] args) {
		generateJSONFile(args[0], args[1]);
	}
	
	public static void generateJSONFile( String path_to_CSN_data_by_Kolhatkar, String path_to_output_file ){
		try {
			BufferedWriter write = new BufferedWriter(new FileWriter(path_to_output_file) );
			write.write("[");
	        write.newLine();
	        Files.walk(Paths.get(path_to_CSN_data_by_Kolhatkar)).forEach(filePath -> {
				try {
					if( Files.isRegularFile(filePath) && !Files.isHidden(filePath) ) {
						String extension = filePath.toString().substring(filePath.toString().lastIndexOf(".") + 1, filePath.toString().length());
						if( extension.equals( "txt" ) ){
							try{
								FileInputStream fstream = new FileInputStream( filePath.toString() );
								DataInputStream in = new DataInputStream( fstream );
								BufferedReader br = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );
								
								String shellNoun = filePath.toString().substring(filePath.toString().lastIndexOf("/") + 1, filePath.toString().lastIndexOf("."));
								shellNoun = shellNoun.toLowerCase();
								
								String line;
								String sentence = "";
								String originalAntecedent = "";
								while( ( line = br.readLine() ) != null ){
									if( line.startsWith("SENTENCE:") ){
										sentence = line.split("SENTENCE:")[1];
									}
									else if( line.startsWith("CONTENT:") ){
										originalAntecedent = line.split("CONTENT:")[1];
									}
									else if( line.startsWith("-") ){
										if( !sentence.isEmpty() && !originalAntecedent.isEmpty() ){
	
											Sentence sent = new Sentence(sentence);
											String lemmatizedSentence = String.join(" ", sent.lemmas());
											
											String artificialSourceSuggestion = sentence.replace(originalAntecedent.trim() + " ", "");
											
											LinkedList<String> newwords = new LinkedList<String>( Arrays.asList(artificialSourceSuggestion.split(" ")) );
															
											newwords.removeAll(Collections.singleton(""));	// make sure no empty strings are in list
											
											int posOfSN = newwords.indexOf(shellNoun);
											String anaphor = "";
											Tree parsedSentence = sent.parse();
											/*
											 * The following three blocks replace shell nouns with "this", thus creating artifical anaphors.
											 */
											 
											// If pattern is: <SN> <Wh> .... be X
											// 			->	  <SN> <Wh> .... be this
											if( ( shellNoun.equals("reason") && ( lemmatizedSentence.matches(".*reason when.*") || lemmatizedSentence.matches(".*reason why.*") || lemmatizedSentence.matches(".*reason that.*") ) ) ||
													( shellNoun.equals("question") && ( lemmatizedSentence.matches(".*question that.*") || lemmatizedSentence.matches(".*question to.*") ) ) ||
													( shellNoun.equals("issue") && lemmatizedSentence.matches(".*issue that.*") ) ){
												
												LinkedList<String> sentenceList = new LinkedList<String>( Arrays.asList(sentence.split(" ") ) );
												LinkedList<String> antecList = new LinkedList<String>( Arrays.asList(originalAntecedent.trim().split(" ") ) );
												
												int posOfAntec = Collections.indexOfSubList(sentenceList, antecList);
												if( posOfAntec != -1 ){
													//System.out.println("Lemma before antecedent in newwords: " + newwords.get(posOfAntec - 1));		// ALL OF THESE ARE "be" WORDS!
													//System.out.println();
													newwords.set(posOfAntec, "this");
												}
											}
											/*
											 * There must be an empty space after "be" otherwise things like "decision because" get matched as well.
											 */
											else if( lemmatizedSentence.matches(".*" + shellNoun + " be\\s.*") ){
												if( posOfSN + 2 < newwords.size() ){
													newwords.set(posOfSN + 2, "this");
													anaphor = "this";
												}
												else{
													anaphor = "NONE";
												}
											}
											else{											
			        					        for( Tree l : parsedSentence.getLeaves() ){
			        					        	if( l.label().value().matches(shellNoun) ) {			        		
			        					        		Tree parent = l.parent(parsedSentence).parent(parsedSentence);
			        					        		List<Tree> partnerleaves = parent.getLeaves();
			        					        		
			        					        		int counter = 0;
			        					        		for( Tree ltree : partnerleaves ){
			        					        			if( ltree.label().value().equals(shellNoun) ){
			        					        				break;
			        					        			}
			        					        			else{
				        					        		    counter++;
			        					        			}
			        					        		}
			        					        		int replaceAt = posOfSN - counter;
			        					        		if( replaceAt == 0 ){
			        					        			newwords.set(replaceAt, "-X-This");
			        					        			anaphor = "This" + " " + shellNoun;
			        					        		}
			        					        		else{
			        					        			newwords.set(replaceAt, "-X-this");
			        					        			anaphor = "this" + " " + shellNoun;
			        					        		}
			        					        		
			        					        		newwords.set(posOfSN, shellNoun + "-X-");
		        					        			
		        					        			List<String> toRemove = new ArrayList<String>();
			        					        		for( int i = replaceAt + 1; i < posOfSN; i++ ){
			        					        			toRemove.add(newwords.get(i));
			        					        		}
			        					        		if( !toRemove.isEmpty() ){
			        					        			newwords.removeAll(toRemove);
			        					        		}
			        					        		break;
			        					        	}
			        					        }
											}
			        					    
											String antecedent = originalAntecedent;
											if( antecedent.startsWith("that") ){
												antecedent.replaceFirst("that ", "");
											}
											/*
											 * TODO: This sentencefication is not needed! Currently, this gets resolved
											 * through the post-processing in @pa.csn.CSNPostProcessing.
											 */
			        					    antecedent = antecedent.substring(0, 1).toUpperCase() + antecedent.substring(1);
			        					    antecedent = antecedent + " .";
			        					        
		        					        Triple<List<String>, List<String>, String> candidatesAndTags = getCandidates(parsedSentence, originalAntecedent);
		        					        List<String> candidates = candidatesAndTags._1;
		        					        List<String> tags = candidatesAndTags._2;
		        					        String antecedentTag = candidatesAndTags._3;
		        
		        					        JSONObject obj = new JSONObject();
	
		        					        obj.put("sbar_head", shellNoun);
		        					        obj.put("artificial_source", sentence);
		        					        obj.put("artificial_source_suggestion", String.join(" ", newwords));
		        					        
		        					        JSONArray antecedents = new JSONArray();
		        					        antecedents.add(antecedent);
		        					        obj.put("artificial_antecedent", antecedents);
		        					        
		        					        JSONArray candidateList = new JSONArray();
		        					        for( String s : candidates ){
		        					        	candidateList.add(s);
		        					        }
		        					        obj.put("candidates_minus_all_antecedents", candidateList);
		        					        obj.put("candidates_0_minus_all_antecedents", candidateList);
		        					        
		        					        JSONArray tagList = new JSONArray();
		        					        for( String s : tags ){
		        					        	tagList.add(s);
		        					        }
		        					        obj.put("candidates_nodes_minus_all_antecedents", tagList);
		        					        obj.put("candidates_nodes_0_minus_all_antecedents", tagList);
		        					        
		        					        JSONArray antecedentTags = new JSONArray();
		        					        antecedentTags.add(antecedentTag);
		        					        obj.put("artificial_antecedent_node", antecedentTags);
		        					        
		        					        obj.put("path_to_doc", filePath.toString().substring(filePath.toString().lastIndexOf("/") + 1, filePath.toString().length()));
	
		        					        write.write(obj.toJSONString());
		        					        write.write(",");
		        					        write.newLine();
										}
									}
							    }
								br.close();
							}
							catch( Exception e ){
								e.printStackTrace();
							}
						}
					}
				}
				catch( Exception e ){
					e.printStackTrace();
				}
			});
			write.write("]");
			write.flush();
			write.close();
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
	
	/*
	 * Similarity check included.
	 */
	public static Triple<List<String>, List<String>, String> getCandidates( Tree parsedSentence, String antecedent ) {
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
        String antecTag = "";
        for( Tree co : constit ){
        	StringBuilder candidate = new StringBuilder();
        	if( !co.isLeaf() ){
	        	List<Tree> coleaves = co.getLeaves();
	        	for( Tree leaf : coleaves ) {
	        		candidate.append(leaf.label().value()).append(" ");
	            }
	        	if( candidate.toString().trim().matches("\\Q" + originalAntecedent + "\\E") ){
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
        Triple<List<String>, List<String>, String> candToConstitTags = new Triple<List<String>, List<String>, String>(candidates, candidateConstituencyTags, antecTag);
        return candToConstitTags;            
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
}
