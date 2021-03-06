/*
 * -----------------------------------------------------------------------\
 * Lumeer
 *  
 * Copyright (C) 2016 - 2017 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import io.lumeer.engine.IntegrationTestBase;
import io.lumeer.engine.annotation.UserDataStorage;
import io.lumeer.engine.api.LumeerConst;
import io.lumeer.engine.api.data.DataDocument;
import io.lumeer.engine.api.data.DataStorage;
import io.lumeer.engine.api.exception.DbException;
import io.lumeer.engine.controller.CollectionFacade;
import io.lumeer.engine.controller.CollectionMetadataFacade;
import io.lumeer.engine.controller.DatabaseInitializer;
import io.lumeer.engine.controller.LinkingFacade;
import io.lumeer.engine.controller.OrganizationFacade;
import io.lumeer.engine.controller.ProjectFacade;
import io.lumeer.engine.controller.SecurityFacade;
import io.lumeer.engine.controller.UserFacade;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

/**
 * @author <a href="mailto:kubedo8@gmail.com">Jakub Rodák</a>
 */
@RunWith(Arquillian.class)
public class LinkingServiceIntegrationTest extends IntegrationTestBase {

   private final String TARGET_URI = "http://localhost:8080/";

   @Inject
   @UserDataStorage
   private DataStorage dataStorage;

   @Inject
   private LinkingFacade linkingFacade;

   @Inject
   private CollectionFacade collectionFacade;

   @Inject
   private CollectionMetadataFacade collectionMetadataFacade;

   @Inject
   private OrganizationFacade organizationFacade;

   @Inject
   private ProjectFacade projectFacade;

   @Inject
   private DatabaseInitializer databaseInitializer;

   @Inject
   private SecurityFacade securityFacade;

   @Inject
   private UserFacade userFacade;

   @Before
   public void init() {
      databaseInitializer.onProjectCreated(projectFacade.getCurrentProjectCode());
   }

   @Test
   public void testRegister() throws Exception {
      assertThat(linkingFacade).isNotNull();
      assertThat(collectionFacade).isNotNull();
      assertThat(collectionMetadataFacade).isNotNull();
   }

   @Test
   public void testAddDropLink() throws Exception {
      final String collectionAddDrop1 = "lscollectionAddDropOne";
      final String collectionAddDrop2 = "lscollectionAddDropTwo";
      List<String> collections = Arrays.asList(collectionAddDrop1, collectionAddDrop2);
      Map<String, List<String>> ids = createTestData(collections);

      for (String collection : ids.keySet()) {
         securityFacade.addCollectionUserRole(projectFacade.getCurrentProjectCode(), collection, getCurrentUser(), LumeerConst.Security.ROLE_WRITE);
      }

      final String role1 = "role1";
      final String role2 = "role2";
      final String collectionAddDrop1Internal = "collection.lscollectionadddropone_0";
      final String collectionAddDrop2Internal = "collection.lscollectionadddroptwo_0";
      List<String> collectionsInternal = Arrays.asList(collectionAddDrop1Internal, collectionAddDrop2Internal);
      dropLinkingCollections(Arrays.asList(role1, role2), collectionsInternal);

      String col1Id1 = ids.get(collectionAddDrop1Internal).get(0);
      String col2Id1 = ids.get(collectionAddDrop2Internal).get(0);
      String col2Id2 = ids.get(collectionAddDrop2Internal).get(2);

      final Client client = ClientBuilder.newBuilder().build();
      Response response = client.target(TARGET_URI)
                                .path(buildPathPrefix(collectionAddDrop1) + buildAddDropPrefix(role1, collectionAddDrop2, col1Id1, col2Id1))
                                .queryParam("direction", "FROM")
                                .request()
                                .buildPost(Entity.json(new DataDocument()))
                                .invoke();
      response.close();
      assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
   }

   private Map<String, List<String>> createTestData(List<String> collections) throws DbException {
      final int numDocuments = 3;
      Map<String, List<String>> ids = new HashMap<>();
      for (String col : collections) {
         String collection = setUpCollection(col);
         List<String> collIds = new ArrayList<>();
         for (int i = 0; i < numDocuments; i++) {
            String id = dataStorage.createDocument(collection, new DataDocument());
            collIds.add(id);
         }
         ids.put(collection, collIds);
      }
      return ids;
   }

   private String setUpCollection(final String collection) {
      try {
         collectionFacade.dropCollection(collectionMetadataFacade.getInternalCollectionName(collection));
      } catch (DbException e) {
         // nothing to do
      }
      try {
         return collectionFacade.createCollection(collection);
      } catch (DbException e) {
         e.printStackTrace();
      }
      return null;
   }

   private void dropLinkingCollections(final List<String> roles, final List<String> collections) {
      for (String role : roles) {
         for (String col1 : collections) {
            for (String col2 : collections) {
               if (col1.equals(col2)) {
                  continue;
               }
               dataStorage.dropCollection(buildCollectionName(col1, col2, role));
            }
         }
      }
   }

   private String buildCollectionName(final String firstCollectionName, final String secondCollectionName, final String role) {
      return LumeerConst.Linking.PREFIX + "_" + firstCollectionName + "_" + secondCollectionName + "_" + role;
   }

   private String buildPathPrefix(final String collectionName) {
      return PATH_CONTEXT + "/rest/organizations/" + organizationFacade.getOrganizationCode() + "/projects/" + projectFacade.getCurrentProjectCode() + "/collections/" + collectionName + "/links/";
   }

   private String buildAddDropPrefix(final String role, final String targetCollection, final String id, final String targetId) {
      return role + "/collections/" + targetCollection + "/documents/" + id + "/targets/" + targetId;
   }

   private String getCurrentUser() {
      return userFacade.getUserEmail();
   }
}
