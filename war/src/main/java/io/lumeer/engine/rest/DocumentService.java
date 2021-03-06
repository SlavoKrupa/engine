/*
 * -----------------------------------------------------------------------\
 * Lumeer
 *  
 * Copyright (C) 2016 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package io.lumeer.engine.rest;

import io.lumeer.engine.annotation.UserDataStorage;
import io.lumeer.engine.api.LumeerConst;
import io.lumeer.engine.api.constraint.InvalidConstraintException;
import io.lumeer.engine.api.data.DataDocument;
import io.lumeer.engine.api.data.DataStorage;
import io.lumeer.engine.api.exception.CollectionNotFoundException;
import io.lumeer.engine.api.exception.DbException;
import io.lumeer.engine.api.exception.InvalidValueException;
import io.lumeer.engine.api.exception.UnauthorizedAccessException;
import io.lumeer.engine.api.exception.UserCollectionNotFoundException;
import io.lumeer.engine.controller.CollectionMetadataFacade;
import io.lumeer.engine.controller.DocumentFacade;
import io.lumeer.engine.controller.DocumentMetadataFacade;
import io.lumeer.engine.controller.OrganizationFacade;
import io.lumeer.engine.controller.ProjectFacade;
import io.lumeer.engine.controller.SecurityFacade;
import io.lumeer.engine.controller.UserFacade;
import io.lumeer.engine.controller.VersionFacade;
import io.lumeer.engine.util.ErrorMessageBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author <a href="mailto:mat.per.vt@gmail.com">Matej Perejda</a>
 *         <a href="mailto:kubedo8@gmail.com">Jakub Rodák</a>
 */
@Path("/organizations/{organization}/projects/{project}/collections/{collectionName}/documents")
@RequestScoped
public class DocumentService implements Serializable {

   private static final long serialVersionUID = 5645433756019847986L;

   @Inject
   private CollectionMetadataFacade collectionMetadataFacade;

   @Inject
   private DocumentFacade documentFacade;

   @Inject
   private DocumentMetadataFacade documentMetadataFacade;

   @Inject
   private SecurityFacade securityFacade;

   @Inject
   private VersionFacade versionFacade;

   @Inject
   private UserFacade userFacade;

   @Inject
   @UserDataStorage
   private DataStorage dataStorage;

   @PathParam("organization")
   private String organisationCode;

   @PathParam("project")
   private String projectCode;

   @Inject
   private OrganizationFacade organizationFacade;

   @Inject
   private ProjectFacade projectFacade;

   @PostConstruct
   public void init() {
      organizationFacade.setOrganizationCode(organisationCode);
      projectFacade.setCurrentProjectCode(projectCode);
   }

   /**
    * Creates and inserts a new document to specified collection. The method creates the given collection if does not exist.
    *
    * @param collectionName
    *       the name of the collection where the document will be created
    * @param document
    *       the DataDocument object representing a document to be created
    * @return the id of the newly created document
    * @throws DbException
    *       When there is an error working with the database.
    * @throws InvalidConstraintException
    *       When the constraint configuration was wrong.
    * @throws InvalidValueException
    *       When the attribute values did not meet constraint requirements.
    */
   @POST
   @Path("/")
   @Produces(MediaType.APPLICATION_JSON)
   @Consumes(MediaType.APPLICATION_JSON)
   public String createDocument(final @PathParam("collectionName") String collectionName, final DataDocument document) throws DbException, InvalidConstraintException, InvalidValueException {
      if (collectionName == null || document == null) {
         throw new BadRequestException();
      }

      final String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      final DataDocument convertedDocument = collectionMetadataFacade.checkAndConvertAttributesValues(internalCollectionName, document);

      return documentFacade.createDocument(internalCollectionName, convertedDocument);
   }

   /**
    * Drops an existing document in given collection by its id.
    *
    * @param collectionName
    *       the name of the collection where the document is located
    * @param documentId
    *       the id of the document to drop
    * @throws DbException
    *       When there is an error working with the database.
    */
   @DELETE
   @Path("/{documentId}")
   public void dropDocument(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId) throws DbException {
      if (collectionName == null || documentId == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      documentFacade.dropDocument(internalCollectionName, documentId);
   }

   /**
    * Reads the specified document in given collection by its id.
    *
    * @param collectionName
    *       the name of the collection where the document is located
    * @param documentId
    *       the id of the read document
    * @return the DataDocument object representing the read document
    * @throws DbException
    *       When there is an error working with the database.
    * @throws InvalidConstraintException
    *       When the constraint configuration was wrong.
    * @throws InvalidValueException
    *       When it was not possible to properly decode the value.
    */
   @GET
   @Path("/{documentId}")
   @Produces(MediaType.APPLICATION_JSON)
   public DataDocument readDocument(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId) throws DbException, InvalidValueException, InvalidConstraintException {
      if (collectionName == null || documentId == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      return collectionMetadataFacade.decodeAttributeValues(internalCollectionName, documentFacade.readDocument(internalCollectionName, documentId));
   }

   /**
    * Modifies an existing document in given collection by its id and create collection if not exists.
    *
    * @param collectionName
    *       the name of the collection where the existing document is located
    * @param updatedDocument
    *       the DataDocument object representing a document with changes to update
    * @throws DbException
    *       When there is an error working with the data storage.
    * @throws InvalidConstraintException
    *       When the constraint configuration was wrong.
    * @throws InvalidValueException
    *       When the attribute values did not meet constraint requirements.
    */
   @PUT
   @Path("/update/")
   @Consumes(MediaType.APPLICATION_JSON)
   public void updateDocument(final @PathParam("collectionName") String collectionName, final DataDocument updatedDocument) throws DbException, InvalidConstraintException, InvalidValueException {
      if (collectionName == null || updatedDocument == null || updatedDocument.getId() == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      final DataDocument convertedDocument = collectionMetadataFacade.checkAndConvertAttributesValues(internalCollectionName, updatedDocument);

      documentFacade.updateDocument(internalCollectionName, convertedDocument);
   }

   /**
    * Replace an existing document in given collection by its id and create collection if not exists.
    *
    * @param collectionName
    *       the name of the collection where the existing document is located
    * @param replaceDocument
    *       the DataDocument object representing a replacing document
    * @throws DbException
    *       When there is an error working with the data storage.
    * @throws InvalidConstraintException
    *       When the constraint configuration was wrong.
    * @throws InvalidValueException
    *       When the attribute values did not meet constraint requirements.
    */
   @PUT
   @Path("/replace/")
   @Consumes(MediaType.APPLICATION_JSON)
   public void replaceDocument(final @PathParam("collectionName") String collectionName, final DataDocument replaceDocument) throws DbException, InvalidConstraintException, InvalidValueException {
      if (collectionName == null || replaceDocument == null || replaceDocument.getId() == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      final DataDocument convertedDocument = collectionMetadataFacade.checkAndConvertAttributesValues(internalCollectionName, replaceDocument);

      documentFacade.replaceDocument(internalCollectionName, convertedDocument);
   }

   /**
    * Put attribute and value to document metadata.
    *
    * @param collectionName
    *       the name of the collection where the document is located
    * @param documentId
    *       the id of the read document
    * @param attributeName
    *       the meta attribute to put
    * @param value
    *       the value of the given attribute
    * @throws DbException
    *       When there is an error working with the data storage.
    */
   @POST
   @Path("/{documentId}/meta/{attributeName}")
   @Consumes(MediaType.APPLICATION_JSON)
   public void addDocumentMetadata(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId, final @PathParam("attributeName") String attributeName, final Object value) throws DbException {
      if (collectionName == null || documentId == null || attributeName == null || value == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      documentMetadataFacade.putDocumentMetadata(internalCollectionName, documentId, attributeName, value);
   }

   /**
    * Reads the metadata keys and values of specified document.
    *
    * @param collectionName
    *       the name of the collection where the document is located
    * @param documentId
    *       the id of the read document
    * @return the map where key is name of metadata attribute and its value
    * @throws DbException
    *       When there is an error working with the data storage.
    */
   @GET
   @Path("/{documentId}/meta")
   @Produces(MediaType.APPLICATION_JSON)
   public Map<String, Object> readDocumentMetadata(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId) throws DbException {
      if (collectionName == null || documentId == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      return documentMetadataFacade.readDocumentMetadata(internalCollectionName, documentId);
   }

   /**
    * Put attributes and its values to document metadata.
    *
    * @param collectionName
    *       the name of the collection where the document is located
    * @param documentId
    *       the id of the read document
    * @param metadata
    *       map with medatadata attributes and its values
    * @throws DbException
    *       When there is an error working with the data storage.
    */
   @PUT
   @Path("/{documentId}/meta")
   @Consumes(MediaType.APPLICATION_JSON)
   public void updateDocumentMetadata(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId, final DataDocument metadata) throws DbException {
      if (collectionName == null || documentId == null || metadata == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      documentMetadataFacade.updateDocumentMetadata(getInternalName(collectionName), documentId, metadata);
   }

   /**
    * Read all versions of the given document and returns it as a list.
    *
    * @param collectionName
    *       collection name where document is stored
    * @param documentId
    *       id of the document
    * @return list of documents in different version
    * @throws DbException
    *       When there is an error working with the data storage.
    * @throws InvalidConstraintException
    *       When the constraint configuration was wrong.
    * @throws InvalidValueException
    *       When it was not possible to decode the attribute values.
    */
   @GET
   @Path("/{documentId}/versions")
   @Produces(MediaType.APPLICATION_JSON)
   public List<DataDocument> searchHistoryChanges(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId) throws DbException, InvalidValueException, InvalidConstraintException {
      if (collectionName == null || documentId == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      final List<DataDocument> docs = versionFacade.getDocumentVersions(getInternalName(collectionName), documentId);
      final List<DataDocument> convertedDocs = new ArrayList<>();

      for (final DataDocument doc : docs) {
         convertedDocs.add(collectionMetadataFacade.decodeAttributeValues(internalCollectionName, doc));
      }

      return convertedDocs;
   }

   /**
    * Reverts old version of the given document.
    *
    * @param collectionName
    *       the name of the collection
    * @param documentId
    *       id of document to revert
    * @param version
    *       old version to be reverted
    * @throws DbException
    *       When there is an error working with the data storage.
    */
   @POST
   @Path("/{documentId}/versions/{version}")
   public void revertDocumentVersion(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId, final @PathParam("version") int version) throws DbException {
      if (collectionName == null || documentId == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      documentFacade.revertDocument(internalCollectionName, documentId, version);
   }

   /**
    * Drops specific document's attribute
    *
    * @param collectionName
    *       the name of the collection
    * @param documentId
    *       id of document
    * @param attributeName
    *       attribute to delete
    * @throws DbException
    *       When there is an error working with the data storage.
    */
   @DELETE
   @Path("/{documentId}/attribute/{attributeName}")
   public void dropDocumentAttribute(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId, final @PathParam("attributeName") String attributeName) throws DbException {
      if (collectionName == null || documentId == null || attributeName == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      if (!securityFacade.hasCollectionRole(projectCode, internalCollectionName, LumeerConst.Security.ROLE_WRITE)) {
         throw new UnauthorizedAccessException();
      }

      documentFacade.dropAttribute(internalCollectionName, documentId, attributeName);
   }

   /**
    * Gets attribute names of a document.
    *
    * @param collectionName
    *       the name of the collection
    * @param documentId
    *       id of document
    * @return set of document's attributes
    * @throws DbException
    *       When there is an error working with the data storage.
    */
   @GET
   @Path("/{documentId}/attributes/")
   public Set<String> readDocumentAttributes(final @PathParam("collectionName") String collectionName, final @PathParam("documentId") String documentId) throws DbException {
      if (collectionName == null || documentId == null) {
         throw new BadRequestException();
      }
      String internalCollectionName = getInternalName(collectionName);
      checkCollectionExistency(internalCollectionName);

      return documentFacade.getDocumentAttributes(collectionName, documentId);
   }

   /**
    * Returns internal name of the given collection stored in the database.
    *
    * @param collectionOriginalName
    *       original name of the collection given by user
    * @return internal name of the given collection
    * @throws UserCollectionNotFoundException
    *       When the given user collection does not exist.
    */
   private String getInternalName(String collectionOriginalName) throws UserCollectionNotFoundException {
      return collectionMetadataFacade.getInternalCollectionName(collectionOriginalName);
   }

   private void checkCollectionExistency(final String collectionName) throws CollectionNotFoundException {
      if (!dataStorage.hasCollection(collectionName)) {
         throw new CollectionNotFoundException(ErrorMessageBuilder.collectionNotFoundString(collectionName));
      }
   }
}
