
package org.sourcepit.maven.exec.bootstrap;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

/**
 * An implementation of a workspace reader that knows how to search the Maven reactor for artifacts.
 * 
 * @author Jason van Zyl
 */
class ReactorReader implements WorkspaceReader
{

   private Map<String, MavenProject> projectsByGAV;

   private Map<String, List<MavenProject>> projectsByGA;

   private WorkspaceRepository repository;

   public ReactorReader(Map<String, MavenProject> reactorProjects)
   {
      projectsByGAV = reactorProjects;

      projectsByGA = new HashMap<String, List<MavenProject>>(reactorProjects.size() * 2);
      for (MavenProject project : reactorProjects.values())
      {
         String key = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());

         List<MavenProject> projects = projectsByGA.get(key);

         if (projects == null)
         {
            projects = new ArrayList<MavenProject>(1);
            projectsByGA.put(key, projects);
         }

         projects.add(project);
      }

      repository = new WorkspaceRepository("reactor", new HashSet<String>(projectsByGAV.keySet()));
   }

   public static Map<String, MavenProject> getProjectMap(List<MavenProject> projects)
      throws org.apache.maven.DuplicateProjectException
   {
      Map<String, MavenProject> index = new LinkedHashMap<String, MavenProject>();
      Map<String, List<File>> collisions = new LinkedHashMap<String, List<File>>();

      for (MavenProject project : projects)
      {
         String projectId = ArtifactUtils.key(project.getGroupId(), project.getArtifactId(), project.getVersion());

         MavenProject collision = index.get(projectId);

         if (collision == null)
         {
            index.put(projectId, project);
         }
         else
         {
            List<File> pomFiles = collisions.get(projectId);

            if (pomFiles == null)
            {
               pomFiles = new ArrayList<File>(Arrays.asList(collision.getFile(), project.getFile()));
               collisions.put(projectId, pomFiles);
            }
            else
            {
               pomFiles.add(project.getFile());
            }
         }
      }

      if (!collisions.isEmpty())
      {
         throw new org.apache.maven.DuplicateProjectException("Two or more projects in the reactor"
            + " have the same identifier, please make sure that <groupId>:<artifactId>:<version>"
            + " is unique for each project: " + collisions, collisions);
      }

      return index;
   }

   private File find(MavenProject project, Artifact artifact)
   {
      if ("pom".equals(artifact.getExtension()))
      {
         return project.getFile();
      }

      org.apache.maven.artifact.Artifact projectArtifact = findMatchingArtifact(project, artifact);

      if (hasArtifactFileFromPackagePhase(projectArtifact))
      {
         return projectArtifact.getFile();
      }
      else if (!hasBeenPackaged(project))
      {
         // fallback to loose class files only if artifacts haven't been packaged yet

         if (isTestArtifact(artifact))
         {
            if (project.hasLifecyclePhase("test-compile"))
            {
               return new File(project.getBuild().getTestOutputDirectory());
            }
         }
         else
         {
            if (project.hasLifecyclePhase("compile"))
            {
               return new File(project.getBuild().getOutputDirectory());
            }
         }
      }

      // The fall-through indicates that the artifact cannot be found;
      // for instance if package produced nothing or classifier problems.
      return null;
   }

   private boolean hasArtifactFileFromPackagePhase(org.apache.maven.artifact.Artifact projectArtifact)
   {
      return projectArtifact != null && projectArtifact.getFile() != null && projectArtifact.getFile().exists();
   }

   private boolean hasBeenPackaged(MavenProject project)
   {
      return project.hasLifecyclePhase("package") || project.hasLifecyclePhase("install")
         || project.hasLifecyclePhase("deploy");
   }

   /**
    * Tries to resolve the specified artifact from the artifacts of the given project.
    * 
    * @param project The project to try to resolve the artifact from, must not be <code>null</code>.
    * @param requestedArtifact The artifact to resolve, must not be <code>null</code>.
    * @return The matching artifact from the project or <code>null</code> if not found.
    */
   private org.apache.maven.artifact.Artifact findMatchingArtifact(MavenProject project, Artifact requestedArtifact)
   {
      String requestedRepositoryConflictId = getConflictId(requestedArtifact);

      org.apache.maven.artifact.Artifact mainArtifact = project.getArtifact();
      if (requestedRepositoryConflictId.equals(getConflictId(mainArtifact)))
      {
         return mainArtifact;
      }

      Collection<org.apache.maven.artifact.Artifact> attachedArtifacts = project.getAttachedArtifacts();
      if (attachedArtifacts != null && !attachedArtifacts.isEmpty())
      {
         for (org.apache.maven.artifact.Artifact attachedArtifact : attachedArtifacts)
         {
            if (requestedRepositoryConflictId.equals(getConflictId(attachedArtifact)))
            {
               return attachedArtifact;
            }
         }
      }

      return null;
   }

   /**
    * Gets the repository conflict id of the specified artifact. Unlike the dependency conflict id, the repository
    * conflict id uses the artifact file extension instead of the artifact type. Hence, the repository conflict id more
    * closely reflects the identity of artifacts as perceived by a repository.
    * 
    * @param artifact The artifact, must not be <code>null</code>.
    * @return The repository conflict id, never <code>null</code>.
    */
   private String getConflictId(org.apache.maven.artifact.Artifact artifact)
   {
      StringBuilder buffer = new StringBuilder(128);
      buffer.append(artifact.getGroupId());
      buffer.append(':').append(artifact.getArtifactId());
      if (artifact.getArtifactHandler() != null)
      {
         buffer.append(':').append(artifact.getArtifactHandler().getExtension());
      }
      else
      {
         buffer.append(':').append(artifact.getType());
      }
      if (artifact.hasClassifier())
      {
         buffer.append(':').append(artifact.getClassifier());
      }
      return buffer.toString();
   }

   private String getConflictId(Artifact artifact)
   {
      StringBuilder buffer = new StringBuilder(128);
      buffer.append(artifact.getGroupId());
      buffer.append(':').append(artifact.getArtifactId());
      buffer.append(':').append(artifact.getExtension());
      if (artifact.getClassifier().length() > 0)
      {
         buffer.append(':').append(artifact.getClassifier());
      }
      return buffer.toString();
   }

   /**
    * Determines whether the specified artifact refers to test classes.
    * 
    * @param artifact The artifact to check, must not be {@code null}.
    * @return {@code true} if the artifact refers to test classes, {@code false} otherwise.
    */
   private static boolean isTestArtifact(Artifact artifact)
   {
      if ("test-jar".equals(artifact.getProperty("type", "")))
      {
         return true;
      }
      else if ("jar".equals(artifact.getExtension()) && "tests".equals(artifact.getClassifier()))
      {
         return true;
      }
      return false;
   }

   public File findArtifact(Artifact artifact)
   {
      String projectKey = artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();

      MavenProject project = projectsByGAV.get(projectKey);

      if (project != null)
      {
         return find(project, artifact);
      }

      return null;
   }

   public List<String> findVersions(Artifact artifact)
   {
      String key = artifact.getGroupId() + ':' + artifact.getArtifactId();

      List<MavenProject> projects = projectsByGA.get(key);
      if (projects == null || projects.isEmpty())
      {
         return Collections.emptyList();
      }

      List<String> versions = new ArrayList<String>();

      for (MavenProject project : projects)
      {
         if (find(project, artifact) != null)
         {
            versions.add(project.getVersion());
         }
      }

      return Collections.unmodifiableList(versions);
   }

   public WorkspaceRepository getRepository()
   {
      return repository;
   }

}