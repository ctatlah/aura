/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.util.test.diff;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Assert;
import org.auraframework.util.json.JsonEncoder;
import org.auraframework.util.json.JsonReader;
import org.auraframework.util.test.util.UnitTestCase;
import org.xml.sax.SAXException;

public class JsonDiffUtils extends TextDiffUtils {

    public JsonDiffUtils(UnitTestCase test, String goldName) throws Exception {
        super(test, goldName);
    }

    @Override
    public void assertDiff(String test, StringBuilder sb) throws SAXException, IOException,
            ParserConfigurationException {
        Object controlObj = new JsonReader().read(readGoldFile());
        Object testObj = new JsonReader().read(test);
        // for stuff that has ref support, the serIds may be different because
        // of ordering, so resolve them
        controlObj = JsonEncoder.resolveRefs(controlObj);
        testObj = JsonEncoder.resolveRefs(testObj);
        Assert.assertEquals(sb == null ? "Diff from " + getUrl() : sb.toString(), controlObj, testObj);
    }
}
