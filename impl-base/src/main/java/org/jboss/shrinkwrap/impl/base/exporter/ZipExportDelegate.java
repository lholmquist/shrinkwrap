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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.exporter.ArchiveExportException;
import org.jboss.shrinkwrap.impl.base.asset.DirectoryAsset;
import org.jboss.shrinkwrap.impl.base.io.IOUtil;
import org.jboss.shrinkwrap.impl.base.io.StreamErrorHandler;
import org.jboss.shrinkwrap.impl.base.io.StreamTask;
import org.jboss.shrinkwrap.impl.base.path.PathUtil;

public class ZipExportDelegate extends AbstractExporterDelegate<InputStream>
{
   //-------------------------------------------------------------------------------------||
   // Class Members ----------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Logger
    */
   private static final Logger log = Logger.getLogger(ZipExportDelegate.class.getName());

   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * OutputStream to hold the output contents
    */
   private final ByteArrayOutputStream output = new ByteArrayOutputStream(8192);

   /**
    * ZipOutputStream used to write the zip entries
    */
   private ZipOutputStream zipOutputStream;

   /**
    * A Set of Paths we've exported so far (so that we don't write
    * any entries twice)
    */
   private Set<ArchivePath> pathsExported = new HashSet<ArchivePath>();

   //-------------------------------------------------------------------------------------||
   // Constructor ------------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Creates a new exporter delegate for exporting archives as Zip
    */
   public ZipExportDelegate(Archive<?> archive)
   {
      super(archive);
   }

   //-------------------------------------------------------------------------------------||
   // Required Implementations -----------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * {@inheritDoc}
    * @see org.jboss.shrinkwrap.impl.base.exporter.AbstractExporterDelegate#export()
    */
   @Override
   protected void export()
   {
      zipOutputStream = new ZipOutputStream(output);
      // Enclose every IO Operation so we can close up cleanly
      IOUtil.closeOnComplete(zipOutputStream, new StreamTask<ZipOutputStream>()
      {

         @Override
         public void execute(ZipOutputStream stream) throws Exception
         {
            ZipExportDelegate.super.export();
         }

      }, new StreamErrorHandler()
      {

         @Override
         public void handle(Throwable t)
         {
            throw new ArchiveExportException("Failed to export Zip: " + getArchive().getName(), t);
         }

      });
   }

   /**
    * {@inheritDoc}
    * @see org.jboss.shrinkwrap.impl.base.exporter.AbstractExporterDelegate#processAsset(ArchivePath, Asset)
    */
   @Override
   protected void processAsset(final ArchivePath path, final Asset asset)
   {
      // Precondition checks
      if (path == null)
      {
         throw new IllegalArgumentException("Path must be specified");
      }

      if(isParentOfAnyPathsExported(path))
      {
         return;
      }
      
      /*
       * SHRINKWRAP-94
       * Add entries for all parents of this Path
       * by recursing first and adding parents that
       * haven't already been written.
       */
      final ArchivePath parent = PathUtil.getParent(path);
      if (parent != null && !this.pathsExported.contains(parent))
      {
         // If this is not the root
         // SHRINKWRAP-96
         final ArchivePath grandParent = PathUtil.getParent(parent);
         final boolean isRoot = grandParent == null;
         if (!isRoot)
         {
            // Process the parent without any asset (it's a directory)
            this.processAsset(parent, null);
         }
      }
      // Mark if we're writing a directory
      final boolean isDirectory = ((asset == null) || (asset instanceof DirectoryAsset));

      // Get Asset InputStream if the asset is specified (else it's a directory so use null)
      final InputStream assetStream = !isDirectory ? asset.openStream() : null;
      final String pathName = PathUtil.optionallyRemovePrecedingSlash(path.get());

      // If we haven't already written this path
      if (!this.pathsExported.contains(path))
      {
         // Make a task for this stream and close when done
         IOUtil.closeOnComplete(assetStream, new StreamTask<InputStream>()
         {

            @Override
            public void execute(InputStream stream) throws Exception
            {
               // If we're writing a directory, ensure we trail a slash for the ZipEntry
               String resolvedPath = pathName;
               if (isDirectory)
               {
                  resolvedPath = PathUtil.optionallyAppendSlash(resolvedPath);
               }

               // Make a ZipEntry
               final ZipEntry entry = new ZipEntry(resolvedPath);

               // Write the Asset under the same Path name in the Zip
               try{
                  zipOutputStream.putNextEntry(entry);
               }
               catch(final ZipException ze)
               {
                  log.log(Level.SEVERE,pathsExported.toString());
                  throw new RuntimeException(ze);
               }

               // Mark that we've written this Path 
               pathsExported.add(path);

               // Read the contents of the asset and write to the JAR, 
               // if we're not just a directory
               if (!isDirectory)
               {
                  IOUtil.copy(stream, zipOutputStream);
               }

               // Close up the instream and the entry
               zipOutputStream.closeEntry();
            }

         }, new StreamErrorHandler()
         {

            @Override
            public void handle(Throwable t)
            {
               throw new ArchiveExportException("Failed to write asset to Zip: " + pathName, t);
            }

         });
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.shrinkwrap.impl.base.exporter.AbstractExporterDelegate#getResult()
    */
   @Override
   protected InputStream getResult()
   {
      // Flush the output to a byte array
      final byte[] zipContent = output.toByteArray();
      if (log.isLoggable(Level.FINE))
      {
         log.fine("Created Zip of size: " + zipContent.length + " bytes");
      }

      // Make an instream
      final InputStream inputStream = new ByteArrayInputStream(zipContent);

      // Return
      return inputStream;
   }
   
   /**
    * Returns whether or not this Path is a parent of any Paths exported 
    * @param path
    * @return
    */
   //TODO The performance here will degrade geometrically with size of the archive
   private boolean isParentOfAnyPathsExported(final ArchivePath path)
   {
      // For all Paths already exported
      for(final ArchivePath exportedPath :this.pathsExported)
      {
         if( this.isParentOfSpecifiedHierarchy(path, exportedPath)){
            return true;
         }
      }
      
      return false;
   }
   
   /**
    * 
    * @param path
    * @param compare
    * @return
    */
   private boolean isParentOfSpecifiedHierarchy(final ArchivePath path,final ArchivePath compare){
      // If we've reached the root, we're not a parent of any paths already exported
      final ArchivePath parent = PathUtil.getParent(compare);
      if(parent==null)
      {
         return false;
      }
      // If equal to me, yes
      if(path.equals(compare))
      {
         return true;
      }
      
      // Check my parent
      return this.isParentOfSpecifiedHierarchy(path, parent);
   }
}
