package edu.lwjgl_fx_01.ui.model.engine.loaders.assimp;

import static org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_AMBIENT;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_SPECULAR;
import static org.lwjgl.assimp.Assimp.aiGetMaterialColor;
import static org.lwjgl.assimp.Assimp.aiImportFile;
import static org.lwjgl.assimp.Assimp.aiProcess_FixInfacingNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;
import static org.lwjgl.assimp.Assimp.aiTextureType_NONE;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;
import edu.lwjgl_fx_01.ui.utils.Utils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import edu.lwjgl_fx_01.ui.model.engine.SceneFx;
import edu.lwjgl_fx_01.ui.model.engine.graph.MaterialFx;
import edu.lwjgl_fx_01.ui.model.engine.shape3d.SkinningMesh;
import edu.lwjgl_fx_01.ui.model.engine.graph.TextureFx;
import edu.lwjgl_fx_01.ui.model.engine.graph.animation.SkinningMeshTimer;
import edu.lwjgl_fx_01.ui.model.engine.graph.MeshFx;
import edu.lwjgl_fx_01.ui.model.engine.graph.NodeFx;

@SuppressWarnings("restriction")
public class StaticMeshesLoader {
	
    public SceneFx load(String resourcePath, String texturesDir) throws Exception {
        return load(resourcePath, texturesDir,
                aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices | aiProcess_Triangulate
                        | aiProcess_FixInfacingNormals);
    }

    @SuppressWarnings("restriction")
	public SceneFx load(String resourcePath, String texturesDir, int flags) throws Exception {
        AIScene aiScene = aiImportFile(resourcePath, flags);
        if (aiScene == null) {
            throw new Exception("Error loading model");
        }

        int numMaterials = aiScene.mNumMaterials();
        PointerBuffer aiMaterials = aiScene.mMaterials();
        List<MaterialFx> materials = new ArrayList<>();
        for (int i = 0; i < numMaterials; i++) {
            AIMaterial aiMaterial = AIMaterial.create(aiMaterials.get(i));
            processMaterial(aiMaterial, materials, texturesDir);
        }

        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer aiMeshes = aiScene.mMeshes();
        MeshFx[] meshes = new MeshFx[numMeshes];
        List<SkinningMesh> mainMeshes = new ArrayList<>();
        
        for (int i = 0; i < numMeshes; i++) {
            AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
            MeshFx mesh = processMesh(aiMesh, materials);
            meshes[i] = mesh;
        }

        SceneFx mainScene = new SceneFx("main_scene"); 
        
		for (int i = 0; i < numMeshes; i++) {
			
			TriangleMesh mesh = new TriangleMesh();
			mesh.getPoints().setAll(meshes[i].getPoints());
			mesh.getNormals().setAll(meshes[i].getNormals());
			mesh.getTexCoords().setAll(meshes[i].getTexCoords());
			mesh.getFaces().setAll(meshes[i].getFaces());
			if (meshes[i].getNormals().length == 0)
				mesh.setVertexFormat(VertexFormat.POINT_TEXCOORD);
			else	
				mesh.setVertexFormat(VertexFormat.POINT_NORMAL_TEXCOORD);
			
			final MeshView meshView = new MeshView(mesh);

			if (i < materials.size()) {
				meshView.setMaterial(materials.get(i).getPhongMaterial());
				meshView.setDrawMode(DrawMode.FILL);
				meshView.setCullFace(CullFace.BACK);
			} else {
				PhongMaterial material = new PhongMaterial(Color.BURLYWOOD);
				meshView.setMaterial(material);
				meshView.setDrawMode(DrawMode.FILL);
				meshView.setCullFace(CullFace.BACK);
			}

			meshes[i].getChildren().add(meshView);
		}
		mainScene.getChildren().addAll(Arrays.asList(meshes));
        return mainScene;
    }

    protected void processIndices(AIMesh aiMesh, List<Integer> indices) {
        int numFaces = aiMesh.mNumFaces();
        AIFace.Buffer aiFaces = aiMesh.mFaces();
        for (int i = 0; i < numFaces; i++) {
            AIFace aiFace = aiFaces.get(i);
            IntBuffer buffer = aiFace.mIndices();
            while (buffer.remaining() > 0) {
                indices.add(buffer.get());
            }
        }
    }

    @SuppressWarnings({ "restriction", "null" })
	protected void processMaterial(AIMaterial aiMaterial, List<MaterialFx> materials,
            String texturesDir) throws Exception {
        AIColor4D colour = AIColor4D.create();

        AIString path = AIString.calloc();
        Assimp.aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, path, (IntBuffer) null,
                null, null, null, null, null);
        String textPath = path.dataString();
        TextureFx texture = null;
        if (textPath != null && textPath.length() > 0) {
        	String textureFile = texturesDir + "/" + textPath;
            textureFile = textureFile.replace("//", "/");
            System.out.println(textureFile);
            texture = new TextureFx(0, 0, 0);
            texture.setImage(new javafx.scene.image.Image((new File(textureFile)).toURI().toString()));
        }

        Vector4f ambient = MaterialFx.DEFAULT_COLOUR;
        int result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_AMBIENT, aiTextureType_NONE, 0,
                colour);
        if (result == 0) {
            ambient = new Vector4f(colour.r(), colour.g(), colour.b(), colour.a());
        }

        Vector4f diffuse = MaterialFx.DEFAULT_COLOUR;
        result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0,
                colour);
        if (result == 0) {
            diffuse = new Vector4f(colour.r(), colour.g(), colour.b(), colour.a());
        }

        Vector4f specular = MaterialFx.DEFAULT_COLOUR;
        result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, aiTextureType_NONE, 0,
                colour);
        if (result == 0) {
            specular = new Vector4f(colour.r(), colour.g(), colour.b(), colour.a());
        }

        MaterialFx material = new MaterialFx(ambient, diffuse, specular, texture, 1.0f);
       
        materials.add(material);
    }

    private MeshFx processMesh(AIMesh aiMesh, List<MaterialFx> materials) {
        List<Float> points = new ArrayList<>();
        List<Float> textures = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> faces = new ArrayList();
        
        processVertices(aiMesh, points);
        processNormals(aiMesh, normals);
        processTextCoords(aiMesh, textures);
        processIndices(aiMesh, faces);

        float[][] bonesVerteicesWeigth = new float[0][0];

        MeshFx mesh = 	new MeshFx( 		
    			Utils.listToArray(points),
    			Utils.listToArray(normals),
    			Utils.listToArray(textures),
    			Utils.listIntToArray(faces),
        		null,
        		null,
        		null,
        		null
        );

        return mesh;
    }

    protected void processNormals(AIMesh aiMesh, List<Float> normals) {
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        while (aiNormals != null && aiNormals.remaining() > 0) {
            AIVector3D aiNormal = aiNormals.get();
            normals.add(aiNormal.x());
            normals.add(aiNormal.y());
            normals.add(aiNormal.z());
        }
    }

    protected void processTextCoords(AIMesh aiMesh, List<Float> textures) {
        AIVector3D.Buffer textCoords = aiMesh.mTextureCoords(0);
        int numTextCoords = textCoords != null ? textCoords.remaining() : 0;
        for (int i = 0; i < numTextCoords; i++) {
            AIVector3D textCoord = textCoords.get();
            textures.add(textCoord.x());
            textures.add(1 - textCoord.y());
        }
    }

    protected void processVertices(AIMesh aiMesh, List<Float> vertices) {
        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        while (aiVertices.remaining() > 0) {
            AIVector3D aiVertex = aiVertices.get();
            vertices.add(aiVertex.x());
            vertices.add(aiVertex.y());
            vertices.add(aiVertex.z());
        }
    }
    
    public class TypeList {
    	public int GroupId;
    	public String groupName;
    	public List<String> types = new ArrayList<>();
    	public String getGroupNameByTypeName(String inp) {
    		return (types.contains(inp)) ? groupName: "";
    	}
    }
}
