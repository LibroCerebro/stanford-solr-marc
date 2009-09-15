package edu.stanford;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.*;
import org.xml.sax.SAXException;


/**
 * tests for table of contents fields
 * 
 * @author Naomi Dushay
 */
public class TableOfContentsTests extends AbstractStanfordBlacklightTest {
	
    /**
     * table of contents search field should have appropriate properties.
     */
@Test
    public final void testTOCSearchField() 
        throws ParserConfigurationException, IOException, SAXException
    {
        String fldName = "toc_search";
        createIxInitVars("allfieldsTests.mrc");
        assertSearchFldMultValProps(fldName);
    }


	/**
	 * vern_toc_search:  check all search subfields for 505
	 */
@Test
	public final void vernTocSearchAllSubfields()
			throws ParserConfigurationException, IOException, SAXException 
	{
		String fldName = "vern_toc_search";
		createIxInitVars("vernacularSearchTests.mrc");
		assertSearchFldMultValProps(fldName);
		assertSingleResult("505VernSearch", fldName, "a505vern");
		
		assertZeroResults(fldName, "none");
	}

}
