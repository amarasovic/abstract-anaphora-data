package pa.csn;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.nytlabs.corpus.NYTCorpusDocument;
import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;

/**
 * This class is used to create a mapping between all the sentences in the data provided by Kolhatkar
 * and their originating files in the NYT Corpus.
 */
public class NYTMappingDeterminator {
	
	/**
	 * Needs two arguments, first the path to the directory containing the Kolhatkar data set and the combined file
	 * containing all sentences line-by-line (e.g. res/CSN_Varada/), second the path to the NYT corpus (e.g. res/nyt_corpus/),
	 * and third the year to check in the NYT corpus (1987-2007).
	 * If the all-sentence file does not exist, it will be created in the Kolhatkar data directory (res/CSN_Varada/allSentences.txt).
	 * The mapping for the year will be saved in the NYT corpus directory (e.g. res/nyt_corpus/csn-1987-mapping.txt).
	 */
	public static void main(String[] args){
		mapSentences( args[0], args[1], args[2] );
	}
	
	public static void createSentencesFile( String path_to_Kolhatkar_data ){
		try {
			BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( new FileOutputStream(path_to_Kolhatkar_data + "allSentences.txt"), "UTF8" ) );
			Files.walk(Paths.get(path_to_Kolhatkar_data)).forEach(filePath -> {
				try {
					// Check every file except the newly created one
					if( Files.isRegularFile(filePath) && !Files.isHidden(filePath) && !filePath.equals(path_to_Kolhatkar_data + "allSentences.txt") ) {
						try{
							FileInputStream fstream = new FileInputStream( filePath.toString() );
							DataInputStream in = new DataInputStream( fstream );
							BufferedReader br = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );

							String line;
							while( ( line = br.readLine() ) != null ){
								if( line.startsWith("SENTENCE:") ){
									String s = line.split("SENTENCE:")[1];
									bw.write(s);
									bw.newLine();
								}
						    }
							br.close();
						}
						catch( Exception e ){
							e.printStackTrace();
						}
					}
				}
				catch( Exception e ){
					e.printStackTrace();
				}
			});
			bw.flush();
			bw.close();
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
	
	public static void mapSentences( String path_to_Kolhatkar_data, String path_to_nyt_corpus, String year ){
		File f = new File(path_to_Kolhatkar_data + "allSentences.txt");
		// If the sentences file does not exist yet, create it at the specified path
		if( !f.exists() ){ 
    		createSentencesFile(path_to_Kolhatkar_data);
		}
		try {
			List<String> sentencesToLookFor = new ArrayList<String>();
			
			FileInputStream fstream = new FileInputStream( path_to_Kolhatkar_data + "allSentences.txt" );
			DataInputStream in = new DataInputStream( fstream );
			BufferedReader br = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );

			String line;
			while( ( line = br.readLine() ) != null ){
				sentencesToLookFor.add(line);
			}
			br.close();

			int original = sentencesToLookFor.size();
			System.out.println("We have to look for " + sentencesToLookFor.size() + " sentences.");
			
			BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( path_to_nyt_corpus + "csn-" + year + "-mapping.txt" ), "UTF8" ) );
			Files.walk(Paths.get(path_to_nyt_corpus + "data/" + year + "/")).forEach(filePath -> {
				try {
					if( Files.isDirectory(filePath) ){																// res/nyt_corpus/data/1987/01/
						System.out.println("Starting with " + filePath);
						Files.walk(Paths.get(filePath.toString())).forEach(filePath2 -> {
							try {
								if( Files.isDirectory(filePath2) ){													// res/nyt_corpus/data/1987/01/01/
									Files.walk(Paths.get(filePath2.toString())).forEach(filePath3 -> {
										try {
											if( Files.isRegularFile(filePath3) && !Files.isHidden(filePath3) ) {	// res/nyt_corpus/data/1987/01/01/xxxxx.xml
												try{
													boolean foundMapping = false;
													String doc = new String(Files.readAllBytes(Paths.get(filePath3.toString())));
													doc = doc.replaceAll("--", "");
													doc = doc.replaceAll("''", "");
													doc = doc.replaceAll("``", "");
													
													System.out.println(filePath3.toString());
													
													Iterator<String> iter = sentencesToLookFor.iterator();
													while( iter.hasNext() ){
														String sentence = iter.next().toString();
														ArrayList<String> tokens = new ArrayList<String>(Arrays.asList(sentence.split(" ")));
														String newsentence = detokenize(tokens);
														newsentence = newsentence.replaceAll("--", "");
														newsentence = newsentence.replaceAll("''", "");
														newsentence = newsentence.replaceAll("``", "");
														if( doc.contains(newsentence) ){
															foundMapping = true;
															bw.write(sentence + "\t|\t" + filePath3.toString());
															bw.newLine();
															bw.flush();
															iter.remove();
															break;
														}
													}
													
													if( !foundMapping ){
														NYTCorpusDocument test = nytP.parseNYTCorpusDocumentFromFile( new File ( filePath3.toString() ) , false);
																	
														if( test.getBody() != null ){
															Document doc = new Document(test.getBody());
															for( int sindex = 0; sindex < doc.sentences().size(); sindex++ ){
																Sentence sent = doc.sentence(sindex);
																String sentence = sent.toString();
																			
																sentence = sentence.replaceAll("--", "");
																sentence = sentence.replaceAll("''", "");
																sentence = sentence.replaceAll("``", "");
																			
																if( sentencesToLookFor.contains(sentence) ){
																	foundMapping = true;
																	bw.write(sentence + "\t|\t" + filePath3.toString());
																	bw.newLine();
																	bw.flush();
																	sentencesToLookFor.remove(sentence);
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
										catch( Exception e ){
											e.printStackTrace();
										}	
									});
								}
							}
							catch( Exception e ){
								e.printStackTrace();
							}
						});
					}
				}
				catch( Exception e ){
					e.printStackTrace();
				}
			});
			bw.flush();
			bw.close();
			int found = original - sentencesToLookFor.size();
			System.out.println("We got " + found + " sentences.");
			
			// Write all the sentences that were not found to diskx
			BufferedWriter bw2 = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( path_to_Kolhatkar_data + "allSentencesRemaining.txt" ), "UTF8" ) );
			for( String s : sentencesToLookFor ){
				bw2.write(s);
				bw2.newLine();
			}
			bw2.flush();
			bw2.close();
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
        for( int i = 1; i < tokens.size(); i++ ){
            if( noSpaceBefore.contains(tokens.get(i)) || noSpaceAfter.contains(tokens.get(i - 1)) ){
                sentence.append(tokens.get(i));
            }
            else{
                sentence.append(" " + tokens.get(i));
            }

            if( "\"".equals(tokens.get(i - 1)) ){
                if( noSpaceAfter.contains("\"") ){
                    noSpaceAfter.remove("\"");
                    noSpaceBefore.add("\"");
                }
                else{
                    noSpaceAfter.add("\"");
                    noSpaceBefore.remove("\"");
                }
            }
        }
        return sentence.toString();
    }
}
