/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.shrinkwrap.impl.base.exporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.ArchivePath;

/**
 * AbstractExporterDelegate
 * 
 * Abstract delegate used for archive export. 
 * Provides a template for exporters for handling archive contents. 
 * 
 * @author <a href="mailto:baileyje@gmail.com">John Bailey</a>
 * @version $Revision: $
 */
public abstract class AbstractExporterDelegate<T>
{
   //-------------------------------------------------------------------------------------||
   // Class Members ----------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Logger
    */
   private static final Logger log = Logger.getLogger(AbstractExporterDelegate.class.getName());

   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /** 
    * The archive being exported
    */
   private final Archive<?> archive;

   //-------------------------------------------------------------------------------------||
   // Constructor ------------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Creates a new abstract exporter delegate for the provided {@link Archive} 
    */
   protected AbstractExporterDelegate(Archive<?> archive)
   {
      super();
      this.archive = archive;
   }

   /**
    * Primary method providing a template for exporting the contents of an archive
    */
   protected void export()
   {
      // Get archive
      Archive<?> archive = getArchive();
      if (log.isLoggable(Level.FINE))
      {
         log.fine("Exporting archive - " + archive.getName());
      }

      // Obtain all contents
      final Map<ArchivePath, Asset> content = archive.getContent();
      
      // Process in reverse order such that we can check for parent relationships, 
      // and not write directories twice
      final List<ArchivePath> paths = new ArrayList<ArchivePath>(content.keySet());
      Collections.reverse(paths);
      
      // For every Path in the Archive
      for (final ArchivePath entry : paths)
      {
         // Get Asset information
         final ArchivePath path = entry;
         final Asset asset = content.get(entry);

         // Process the asset
         processAsset(path, asset);
      }
   }

   //-------------------------------------------------------------------------------------||
   // Contracts --------------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Template method for processing a single asset.
    * 
    * @param path
    * @param asset
    */
   protected abstract void processAsset(ArchivePath path, Asset asset);

   /**
    * Return the results of the export.  Should process any tasks required to finalize the export.  
    * 
    * @return
    */
   protected abstract T getResult();

   //-------------------------------------------------------------------------------------||
   // Internal Helper Methods ------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Return the archive being exported
    * 
    * @return
    */
   protected Archive<?> getArchive()
   {
      return archive;
   }

}
