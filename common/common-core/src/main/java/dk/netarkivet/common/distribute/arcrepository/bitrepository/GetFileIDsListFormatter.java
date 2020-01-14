/*
 * #%L
 * Netarchivesuite - common
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.common.distribute.arcrepository.bitrepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.bitrepository.commandline.output.OutputHandler;
import org.bitrepository.commandline.outputformatter.GetFileIDsOutputFormatter;
import org.bitrepository.commandline.resultmodel.FileIDsResult;

/**
 * This formatter stores the found fileIds in a List object.
 *
 */
public class GetFileIDsListFormatter implements GetFileIDsOutputFormatter {

    List<String> result = new ArrayList<String>();

    public GetFileIDsListFormatter(OutputHandler outputHandler) {
    }

    @Override
    public void formatHeader() {
    }

    @Override
    public void formatResult(Collection<FileIDsResult> results) {
        results.stream()
                .map(FileIDsResult::getID)
                .forEachOrdered(result_id -> result.add(result_id));
    }
    public List<String> getFoundIds() {
        return Collections.unmodifiableList(result);
    }
}