package org.solrmarc.testUtils;

import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.util.*;

//import org.apache.log4j.Logger;

import org.marc4j.MarcException;
import org.marc4j.marc.Record;

import org.solrmarc.index.*;
import org.solrmarc.marc.MarcHandler;
import org.solrmarc.tools.MarcUtils;
import org.solrmarc.tools.SolrMarcIndexerException;

/**
 * Reads in marc records and creates mapping of solr field names to solr field
 * values per configuration files. Only creates the mapping; does not write out
 * to file or to index.
 * 
 * based on org.solrmarc.marc.MarcPrinter by Bob Haschart
 * 
 * @author Naomi Dushay
 * @version $Id$
 */
public class MarcMappingOnly extends MarcHandler
{
    // static Logger logger = Logger.getLogger(MarcMappingOnly.class.getName());

    /** name of unique key field in solr document */
    private String idFldName = null;
    private String argsPlus[] = null;

    /**
     * Constructor
     * @param args - array of Strings:
     *    arg[0] - name of xxx_config.properties file
     *    arg[1] - name of unique key field in solr document
     */
    public MarcMappingOnly()
    {
        super();
    }
    
    
    @Override
    public void init(String args[]) 
    {
        if (args[0].contains("+"))
        {
            argsPlus = args[0].split("[+]");
            if (argsPlus.length > 0 && argsPlus[0].length() > 0)
                args[0] = argsPlus[0];
            else
                args[0] = "null.properties";
        }
        try
        {
        	super.init(args);
    	}
        catch (FileNotFoundException e)
        {
        	fail("couldn't find file: " + e.getMessage());
        }
    }
    
    @Override
    protected void loadLocalProperties()
    {
        if (argsPlus != null && argsPlus.length > 1)
        {
            for (int i = 1; i < argsPlus.length; i++)
            {
                String argParts[] = argsPlus[i].split("=", 2);
                if (argParts.length == 2)
                {
                    System.err.println("Adding property: "+ argParts[0] + " with value: " + argParts[1]);
                    configProps.setProperty(argParts[0], argParts[1]);
                }
            }
        }
    }

    /** 
     * processAdditionalArgs - local init for subclasses of MarcHandler
     */
    protected void processAdditionalArgs()
    {
        idFldName = addnlArgs[0];
    }  

    /**
     * read in the file of marc records indicated, looking for the desired
     * record, and returning the mapping of solr field names to values.
     * 
     * @param desiredRecId -
     *            value for solr id field, or pass in a value of null to simply accept 
     *            the first record that occurs in the specified marc file
     * @param mrcFileName -
     *            absolute path of file of marc records (name must end in .mrc
     *            or .marc or .xml)
     * @return a mapping of solr field names to solr field values (as Objects
     *         that are Strings or Collections of Strings)
     */
    public Map<String, Object> getIndexMapForRecord(String desiredRecId, String mrcFileName)
    {
    	try
    	{
            loadReader("FILE", mrcFileName);
    	}
        catch (FileNotFoundException e)
        {
        	fail("couldn't find file: " + e.getMessage());
        }
    	
        while (reader != null && reader.hasNext())
        {
            Record record = reader.next();
            try
            {
                Map<String, Object> solrFldName2ValMap = indexer.map(record, errors);
                if (errors != null && includeErrors && errors.hasErrors())
                    solrFldName2ValMap.put("marc_error", errors.getErrors());
                // FIXME:
                if (desiredRecId == null || idFldName == null) 
                	return solrFldName2ValMap;
                
                Object thisRecId = solrFldName2ValMap.get(idFldName);
                if (thisRecId != null && thisRecId.equals(desiredRecId))
                    return solrFldName2ValMap;
            }
            catch (SolrMarcIndexerException e)
            {
                String recCntlNum = null;
                try {
                	recCntlNum = record.getControlNumber();
                }
                catch (NullPointerException npe) { /* ignore */ }

            	if (e.getLevel() == SolrMarcIndexerException.DELETE)
            		System.err.println("Indexing specs say record " + (recCntlNum != null ? recCntlNum : "") + " should not be indexed");
            	else
	                System.err.println("Error indexing Marc Record " + (recCntlNum != null ? recCntlNum : "") + ":" + e.getMessage());
            }
            catch (MarcException me)
            {
                System.err.println("Error reading Marc Record: " + me.getMessage());
            }
        }
        return null;
    }
    
    /**
     * read in the file of marc records indicated, looking for the desired
     * record, and return the specified field/fields according to the provided fieldSpec
     * 
     * @param desiredRecId -
     *            value for solr id field, or pass in a value of null to simply accept 
     *            the first record that occurs in the specified marc file
     * @param mrcFileName -
     *            absolute path of file of marc records (name must end in .mrc
     *            or .marc or .xml)
     * @param fieldSpec -
     *            a raw SolrMarc-type field specification, for testing the lower level functions of 
     *            SolrMarc without first processing a full indexing specification.
     * @return the field/subfields from the indicated record as specified by the fieldSpec parameter
     * @throws FileNotFoundException 
     */
    public Set<String> lookupRawRecordValue(String desiredRecId, String mrcFileName, String fieldSpec) 
    {
    	try
    	{
            loadReader("FILE", mrcFileName);
    	}
        catch (FileNotFoundException e)
        {
        	fail("couldn't find file: " + e.getMessage());
        }
        String propertyFilePathStr = System.getProperty("solrmarc.path");
        String propertyFilePaths[]  = makePropertySearchPath(propertyFilePathStr, null, null, homeDir);
  //      String propertyFilePaths[] = propertyFilePathStr == null ? new String[0] : propertyFilePathStr.split("[|]");
        while (reader != null && reader.hasNext())
        {
            try
            {
                Record record = reader.next();

                String thisRecId = record.getControlNumber();
                if (desiredRecId != null && !thisRecId.equals(desiredRecId)) 
                	continue;

                Set<String> result = null;
                String translationMap = null;
                if (fieldSpec.matches(".*[{].*[}]"))
                {
                    translationMap = fieldSpec.replaceAll(".*[{](.*)[}]", "$1");
                    fieldSpec = fieldSpec.substring(0, fieldSpec.indexOf('{'));
                }
                if (fieldSpec.matches("^[0-9].*") || fieldSpec.matches("^LNK[0-9].*")) // if it is a standard 245a type field spec
                {
                    result = MarcUtils.getFieldList(record, fieldSpec);
                    if (translationMap != null)
                    {
                        Properties indexingProps = new Properties();
                        indexingProps.setProperty("marcmappingtest", fieldSpec + ", " + translationMap );
                        SolrIndexer indexer = SolrIndexer.indexerFromProperties(indexingProps, propertyFilePaths);
                        String translationMapName = indexer.loadTranslationMap(translationMap);
                        result = org.solrmarc.tools.Utils.remap(result, indexer.findMap(translationMapName), true);
                    }
                }
                else if (fieldSpec.contains("(rec"))
                {
                    String indexParm = fieldSpec.replaceFirst("\\(rec ?,?", "(");
                    Properties indexingProps = new Properties();
                    if (translationMap != null)
                        indexingProps.setProperty("marcmappingtest", "custom, "+ indexParm + ", " + translationMap);
                    else
                        indexingProps.setProperty("marcmappingtest", "custom, "+ indexParm);
                    SolrIndexer indexer = SolrIndexer.indexerFromProperties(indexingProps, propertyFilePaths);
                    Map<String, Object> indexMap = indexer.map(record);
                    Object tmpResult = indexMap.get("marcmappingtest");
                    if (tmpResult instanceof Set)
                        result = (Set<String>)tmpResult;
                    else if (tmpResult instanceof String)
                    {
                        result = new LinkedHashSet<String>();
                        result.add((String)tmpResult);
                    }
                }
                else if (fieldSpec.startsWith("\""))
                {
                    Properties indexingProps = new Properties();
                    indexingProps.setProperty("marcmappingtest", fieldSpec);
                    SolrIndexer indexer = SolrIndexer.indexerFromProperties(indexingProps, propertyFilePaths);
                    Map<String, Object> indexMap = indexer.map(record);
                    Object tmpResult = indexMap.get("marcmappingtest");
                    if (tmpResult instanceof Set)
                        result = (Set<String>)tmpResult;
                    else if (tmpResult instanceof String)
                    {
                        result = new LinkedHashSet<String>();
                        result.add((String)tmpResult);
                    }
                }
                else if (fieldSpec.startsWith("'") && fieldSpec.endsWith("'"))
                {
                    String indexParm = fieldSpec.substring(1, fieldSpec.length()-1);
                    Properties indexingProps = new Properties();
                    indexingProps.setProperty("marcmappingtest", indexParm);
                    SolrIndexer indexer = SolrIndexer.indexerFromProperties(indexingProps, propertyFilePaths);
                    Map<String, Object> indexMap = indexer.map(record);
                    Object tmpResult = indexMap.get("marcmappingtest");
                    if (tmpResult instanceof Set)
                        result = (Set<String>)tmpResult;
                    else if (tmpResult instanceof String)
                    {
                        result = new LinkedHashSet<String>();
                        result.add((String)tmpResult);
                    }
                }
                return(result);
            }
            catch (MarcException me)
            {
                System.err.println("Error reading Marc Record: " + me.getMessage());
            }
        }
        return null;
    }

    @Override
    /**
     * this method is required, though we don't use it here.
     */
    public int handleAll()
    {
        return 0;
    }

}