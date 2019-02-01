package pa.csn;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.trees.Tree;

/**
 * This class was used to post-process the complete CSN JSON file.
 * Specifically, we removed duplicate candidates and normalized artifical antecedents.
 */
public class CSNPostProcessing {

	/**
	 * NOTE: As opposed to other classes in this package, the hard-coded file paths remain
	 * for demonstrative purposes as to the post-processing pipeline.
	 */
	public static void main( String[] args ) {
		removeAndUnalter("res/CSN/csn_with_markers.json", "res/CSN/csn_no_duplicates.json");
		markUnmarkedInstances("res/CSN/csn_no_duplicates.json", "res/CSN/csn_all_marked.json");
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
	
	/**
 	 * This method removes duplicate candidates from given JSONArrays of candidates and candidate tags.
 	 */
	public static Tuple<JSONArray, JSONArray> removeDuplicates( JSONArray candidates, JSONArray candidate_tags ){
		List<String> candidates_old = (ArrayList) candidates;
		List<String> candidateConstituencyTags = (ArrayList) candidate_tags;
		
		List<String> finalCandidates = new ArrayList<String>();
	    List<String> finalCandidateConstituencyTags = new ArrayList<String>();
	    Map<String, Map<String, Integer>> map = new HashMap<String, Map<String, Integer>>();	// {duplicate-candidate -> {tag1->#occ, tag2->#occ}}
		Set<String> set1 = new HashSet<String>();
		for( int i = 0; i < candidates_old.size(); i++ ){
			String cand = candidates_old.get(i);
			if( !set1.add(cand) ){
				String tag = candidateConstituencyTags.get(i);
				if( !map.containsKey(cand) ){
					int indexOfFirstCandOcc = candidates_old.indexOf(cand);
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
		
		for( int t = 0; t < candidates_old.size(); t++ ){
			String aCandidate = candidates_old.get(t);
			if( !finalCandidates.contains(aCandidate) ){
				finalCandidates.add(aCandidate);
				finalCandidateConstituencyTags.add(candidateConstituencyTags.get(t));
			}
		}
		
		JSONArray final_candidates = new JSONArray();
		JSONArray final_candidate_tags = new JSONArray();
		for( String cand : finalCandidates ){
			final_candidates.add(cand);
		}
		for( String tag : finalCandidateConstituencyTags ){
			final_candidate_tags.add(tag);
		}
		
		Tuple<JSONArray, JSONArray> result = new Tuple<JSONArray, JSONArray>(final_candidates, final_candidate_tags);
		return result;
	}
	
	/**
	 * Check position of artificial_antecedent in artificial_source.
	 * If the former is not at the beginning of the latter, lower-case and remove punctuation and return modified artificial_antecedent.
	 * Else, only removes punctuation.
	 * 
	 * @param art_antecedent
	 * @param art_source
	 * @return antecedent
	 */
	public static String unalterArtificialAntecedent( String art_antecedent, String art_source ){
		String new_antecedent = "";
		if( art_antecedent.trim().lastIndexOf(".") == art_antecedent.length() - 1 ){
			new_antecedent = art_antecedent.substring(0, art_antecedent.lastIndexOf(".")).trim();
		}
		if( art_source.indexOf(new_antecedent) != 0 ){
			new_antecedent = new_antecedent.substring(0, 1).toLowerCase() + new_antecedent.substring(1);
		}
		if( new_antecedent.isEmpty() ){
			return art_antecedent;
		}
		else{
			return new_antecedent;
		}
	}
	
	/**
 	 * This method removes duplicate candidates from given JSON file and unalters the artifical_antecedent.
 	 */
	public static void removeAndUnalter( String path_to_input_file, String path_to_output_file){
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(path_to_output_file) );
			writer.write("[");
	        writer.newLine();
	        
	        JSONParser parser = new JSONParser();
			try{
				System.out.println("Parsing file...");
				JSONArray full_csn = (JSONArray) parser.parse(new FileReader(path_to_input_file));
				System.out.println("Done!");
				for( int i = 0; i < full_csn.size(); i++ ){
					Object o = full_csn.get(i);
					JSONObject testsetentry = (JSONObject) o;
					JSONObject obj = testsetentry;
					
					JSONArray art_antecedents = (JSONArray) testsetentry.get("artificial_antecedent");
					String art_source = (String) testsetentry.get("artificial_source");
					JSONArray new_art_antecedent = new JSONArray();
					for( Object oInA : art_antecedents ){
						String antecTmp = (String) oInA;
						String newAntecTmp = unalterArtificialAntecedent(antecTmp, art_source);
						new_art_antecedent.add(newAntecTmp);
					}
					obj.put("artificial_antecedent", new_art_antecedent);
					
					JSONArray allAntCand = (JSONArray) testsetentry.get("candidates_minus_all_antecedents");
					JSONArray allAntCandTags = (JSONArray) testsetentry.get("candidates_nodes_minus_all_antecedents");
					Tuple<JSONArray, JSONArray> noDuplicates = removeDuplicates(allAntCand, allAntCandTags);
					obj.put("candidates_minus_all_antecedents", noDuplicates._1);
					obj.put("candidates_nodes_minus_all_antecedents", noDuplicates._2);
					
					JSONArray allCand0 = (JSONArray) testsetentry.get("candidates_0_minus_all_antecedents");
					JSONArray allCandTags0 = (JSONArray) testsetentry.get("candidates_nodes_0_minus_all_antecedents");
					Tuple<JSONArray, JSONArray> noDuplicates0 = removeDuplicates(allCand0, allCandTags0);
					obj.put("candidates_0_minus_all_antecedents", noDuplicates0._1);
					obj.put("candidates_nodes_0_minus_all_antecedents", noDuplicates0._2);
					
					if( testsetentry.containsKey("candidates_1_minus_all_antecedents") ){
						JSONArray allCand1 = (JSONArray) testsetentry.get("candidates_1_minus_all_antecedents");
						JSONArray allCandTags1 = (JSONArray) testsetentry.get("candidates_nodes_1_minus_all_antecedents");
						Tuple<JSONArray, JSONArray> noDuplicates1 = removeDuplicates(allCand1, allCandTags1);
						obj.put("candidates_1_minus_all_antecedents", noDuplicates1._1);
						obj.put("candidates_nodes_1_minus_all_antecedents", noDuplicates1._2);
					
						if( testsetentry.containsKey("candidates_2_minus_all_antecedents") ){
							JSONArray allCand2 = (JSONArray) testsetentry.get("candidates_2_minus_all_antecedents");
							JSONArray allCandTags2 = (JSONArray) testsetentry.get("candidates_nodes_2_minus_all_antecedents");
							Tuple<JSONArray, JSONArray> noDuplicates2 = removeDuplicates(allCand2, allCandTags2);
							obj.put("candidates_2_minus_all_antecedents", noDuplicates2._1);
							obj.put("candidates_nodes_2_minus_all_antecedents", noDuplicates2._2);
						
							if( testsetentry.containsKey("candidates_3_minus_all_antecedents") ){
								JSONArray allCand3 = (JSONArray) testsetentry.get("candidates_3_minus_all_antecedents");
								JSONArray allCandTags3 = (JSONArray) testsetentry.get("candidates_nodes_3_minus_all_antecedents");
								Tuple<JSONArray, JSONArray> noDuplicates3 = removeDuplicates(allCand3, allCandTags3);
								obj.put("candidates_3_minus_all_antecedents", noDuplicates3._1);
								obj.put("candidates_nodes_3_minus_all_antecedents", noDuplicates3._2);
							}
						}
					}

				    writer.write(obj.toJSONString());
				    if( i != full_csn.size() - 1 ){
				    	writer.write(",");
				    }
				    writer.newLine();
				    writer.flush();
				}
			}
			catch( Exception e ){
				e.printStackTrace();
			}
			writer.write("]");
			writer.flush();
			writer.close();
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
	
	/**
	 * Since there were still some instances (21 out of 114,753) that had no marked anaphor,
	 * we used this method to resolve as many of those 21 instances as possible. In the end, 14 out of the 21
	 * instances were marked by this method, and we dropped the last seven instances
	 * (total number of instances: 114,746).
	 *
	 * @param path_to_input_file (e.g. res/CSN/csn_no_duplicates.json)
	 * @param path_to_output_file (e.g. res/CSN/csn_all_marked.json)
	 */
	public static void markUnmarkedInstances( String path_to_input_file, String path_to_output_file ){
		JSONParser parser = new JSONParser();
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(path_to_output_file) );
			writer.write("[");
	        writer.newLine();
	        
			System.out.println("Parsing file...");
			JSONArray full_csn = (JSONArray) parser.parse(new FileReader(path_to_input_file));
			System.out.println("Done!");
			for( int i = 0; i < full_csn.size(); i++ ){
				Object o = full_csn.get(i);
				JSONObject testsetentry = (JSONObject) o;
				JSONObject obj = testsetentry;
				
				String art_source_suggestion = (String) testsetentry.get("artificial_source_suggestion");
				if( !art_source_suggestion.contains("-X-") ){
					String source = (String) testsetentry.get("artificial_source");
					JSONArray art_antecedents = (JSONArray) testsetentry.get("artificial_antecedent");
					String antecedent = (String) art_antecedents.get(0);
					String shellNoun = (String) testsetentry.get("sbar_head");
					
					LinkedList<String> newwords = new LinkedList<String>();
					
					if( antecedent.contains("  ") ){
						if( antecedent.startsWith("no.") ){
							antecedent = antecedent.substring(0, 1).toUpperCase() + antecedent.substring(1);
						}
						String[] doubleSpaceAntecedent = antecedent.split("  ");
						LinkedList<String> sourceList = new LinkedList<String>( Arrays.asList( source.split(" ") ) );
						LinkedList<String> dsa1 = new LinkedList<String>( Arrays.asList( doubleSpaceAntecedent[0].split(" ") ) );
						LinkedList<String> dsa2 = new LinkedList<String>( Arrays.asList( doubleSpaceAntecedent[1].split(" ") ) );
						int posOfDsa1 = Collections.indexOfSubList(sourceList, dsa1);
						int posOfDsa2 = Collections.indexOfSubList(sourceList, dsa2);
						
						LinkedList<String> recoveredAntecedent = new LinkedList<String>();
						if( posOfDsa1 != -1 && posOfDsa2 != -1 ){
							for( int pos = posOfDsa1; pos < posOfDsa2; pos++ ){
								String tmpWord = sourceList.get(pos);
								recoveredAntecedent.add(tmpWord);
							}
							for( String s : dsa2 ){
								recoveredAntecedent.add(s);
							}
						}
						
						if( recoveredAntecedent.isEmpty() ){
							antecedent = antecedent.replaceAll("  ", " ");
						}
						else{
							antecedent = String.join(" ", recoveredAntecedent);
						}
						String newArtificialSourceSuggestion = source.replace(antecedent.trim() + " ", "");
						newwords = new LinkedList<String>( Arrays.asList(newArtificialSourceSuggestion.split(" ")) );
					}
					else{
						newwords = new LinkedList<String>( Arrays.asList(art_source_suggestion.split(" ")) );
					}
					int posOfSN = newwords.indexOf(shellNoun);
					
					Sentence originalSent = new Sentence(source);
					String lemmatizedSentence = String.join(" ", originalSent.lemmas());
					
					Sentence sent = new Sentence(String.join(" ", newwords));
					Tree parsedSentence = sent.parse();

					if( ( shellNoun.equals("reason") && ( lemmatizedSentence.matches(".*reason when.*") || lemmatizedSentence.matches(".*reason why.*") || lemmatizedSentence.matches(".*reason that.*") ) ) ||
							( shellNoun.equals("question") && ( lemmatizedSentence.matches(".*question that.*") || lemmatizedSentence.matches(".*question to.*") ) ) ||
							( shellNoun.equals("issue") && lemmatizedSentence.matches(".*issue that.*") ) ){
						LinkedList<String> sentenceList = new LinkedList<String>( Arrays.asList(source.split(" ") ) );
						LinkedList<String> antecList = new LinkedList<String>( Arrays.asList(antecedent.trim().split(" ") ) );	
						
						int posOfAntec = Collections.indexOfSubList(sentenceList, antecList);
						if( posOfAntec != -1 ){
							newwords.set(posOfAntec, "-X-this-X-");
							if( posOfAntec == newwords.size() - 1) {
								newwords.add(".");
							}
							else if( posOfAntec == newwords.size() - 2 && newwords.get(newwords.size() - 1).equals("-RRB-") ){
								newwords.set(newwords.size() - 1, ".");
								newwords.add("-RRB-");
							}
							else if( newwords.get(posOfAntec + 1).equals("''") ){
								newwords.add(posOfAntec + 1, ",");
							}
						}
					}
					else if( lemmatizedSentence.matches(".*" + shellNoun + " be .*") ){
						if( posOfSN + 2 < newwords.size() ){
							newwords.set(posOfSN + 2, "this");
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
				        		}
				        		else{
				        			newwords.set(replaceAt, "-X-this");
				        		}
				        		newwords.set(posOfSN, shellNoun + "-X-");
				    			
				    			List<String> toRemove = new ArrayList<String>();
				        		for( int j = replaceAt + 1; j < posOfSN; j++ ){
				        			toRemove.add(newwords.get(j));
				        		}
				        		if( !toRemove.isEmpty() ){
				        			newwords.removeAll(toRemove);
				        		}
				        		break;
				        	}
				        }
					}
					if( String.join(" ", newwords).contains("-X-") ){
				        JSONArray new_antecedents = new JSONArray();
				        new_antecedents.add(antecedent);
				        obj.put("artificial_antecedent", new_antecedents);
				        obj.put("artificial_source_suggestion", String.join(" ", newwords));
				        
				        writer.write(obj.toJSONString());
					    if( i != full_csn.size() - 1 ){
					    	writer.write(",");
					    }
					    writer.newLine();
					    writer.flush();
					}
				}
				else{
					writer.write(obj.toJSONString());
				    if( i != full_csn.size() - 1 ){
				    	writer.write(",");
				    }
				    writer.newLine();
				    writer.flush();
				}
			}
			writer.write("]");
			writer.flush();
			writer.close();
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
}
