/*
 * #%L
 * Netarchivesuite - archive
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

package dk.netarkivet.archive.arcrepositoryadmin;

import dk.netarkivet.archive.ArchiveSettings;
import dk.netarkivet.common.utils.SettingsFactory;

/**
 * Factory class for the admin instance. This creates an instance of the admin structure, which is defined by the
 * settings.
 *
 * @see dk.netarkivet.archive.ArchiveSettings#ADMIN_CLASS
 */
public class AdminFactory extends SettingsFactory<Admin> {
    /**
     * Retrieves the admin instance defined in the settings.
     *
     * @return The settings defined admin instance.
     */
    public static Admin getInstance() {
        return SettingsFactory.getInstance(ArchiveSettings.ADMIN_CLASS);
    }
}
