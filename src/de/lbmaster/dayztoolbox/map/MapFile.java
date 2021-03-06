package de.lbmaster.dayztoolbox.map;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.lbmaster.dayztoolbox.utils.ByteUtilsBE;

public class MapFile {

	private File file;
	private List<MapObject> content = new ArrayList<MapObject>();
	private List<MapObjectHeader> contentHeaders = new ArrayList<MapObjectHeader>();

	public boolean fullyReadContent = false;
	private boolean readPositions = true;
	private boolean readImages = true;

	public boolean hasFullyReadContent() {
		return fullyReadContent;
	}

	private MapFileHeader header;

	public MapFile(File file) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}

	public void removeAllPositions() {
		List<MapObject> copy = new ArrayList<MapObject>();
		copy.addAll(content);
		for (MapObject obj : copy) {
			if (obj != null && obj.getType().equals(MapObjectType.MAP_POINTS) && obj instanceof MapPositions)
				removeMapObject(obj);
		}
	}

	public void removeAllImages() {
		List<MapObject> copy = new ArrayList<MapObject>();
		copy.addAll(content);
		for (MapObject obj : copy) {
			if (obj != null && obj.getType().equals(MapObjectType.MAP_IMAGE) && obj instanceof MapImage)
				removeMapObject(obj);
		}
	}

	public void removeMapObject(MapObject obj) {
		content.remove(obj);
	}

	public void addMapObject(MapObject obj) {
		if (obj instanceof MapPositions && getPositionsByDisplayName(((MapPositions) obj).getDisplayName()) != null) {
			MapPositions parent = getPositionsByDisplayName(((MapPositions) obj).getDisplayName());
			MapPositions child = ((MapPositions) obj);
			for (MapPosition pos : child.getPositions()) {
				parent.addPosition(pos);
			}
		} else {
			content.add(obj);
		}
	}

	public List<MapObject> getContent() {
		return content;
	}

	public MapPositions getPlayerSpawns() {
		return getPositionsByName(MapPositions.MAP_POSITIONS_NAME_PLAYER_SPAWNPOINTS);
	}
	
	public List<MapPositions> getAllEventSpawns() {
		List<MapPositions> list = new ArrayList<MapPositions>();
		for (MapPositions pos : getAllPositions()) {
			if (!pos.getName().equals(MapPositions.MAP_POSITIONS_NAME_PLAYER_SPAWNPOINTS)) {
				list.add(pos);
			}
		}
		return list;
	}

	public MapPositions getPositionsByName(String name) {
		for (MapPositions pos : getAllPositions()) {
			if (pos != null && pos.getName().equals(name))
				return pos;
		}
		return null;
	}

	public MapPositions getPositionsByDisplayName(String displayname) {
		for (MapPositions pos : getAllPositions()) {
			if (pos != null && pos.getDisplayName().equals(displayname))
				return pos;
		}
		return null;
	}

	public List<MapPositions> getAllPositions() {
		List<MapPositions> positions = new ArrayList<MapPositions>();
		for (MapObject obj : getContent()) {
			if (obj != null && obj.getType().equals(MapObjectType.MAP_POINTS) && obj instanceof MapPositions)
				positions.add((MapPositions) obj);
		}
		return positions;
	}

	public List<MapImage> getAllImages() {
		List<MapImage> positions = new ArrayList<MapImage>();
		for (MapObject obj : getContent()) {
			if (obj != null && obj.getType().equals(MapObjectType.MAP_IMAGE) && obj instanceof MapImage)
				positions.add((MapImage) obj);
		}
		return positions;
	}

	public void save(File file) throws IOException {
		
		FileOutputStream out = new FileOutputStream(file);
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		contentHeaders.clear();
		for (MapObject obj : getContent()) {
			byte[] con = obj.toBytes();
			content.write(con);
			contentHeaders.add(new MapObjectHeader(con.length, obj.getType(), -1));
		}
		ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream(contentHeaders.size() * MapFileHeader.CONTENT_HEADER_SIZE);
		for (MapObjectHeader head : contentHeaders) {
			headerBuffer.write(head.toBytes());
		}
		byte[] header = headerBuffer.toByteArray();
		byte[] head = generateHead(header);
		System.out.println("Head: " + ByteUtilsBE.bytesToHex(head));
		System.out.println("Header: " + ByteUtilsBE.bytesToHex(header));
		out.write(head);
		out.write(ByteUtilsBE.shortToBytes((short) contentHeaders.size()));
		out.write(header);
		out.write(content.toByteArray());
		out.flush();
		out.close();
	}

	private byte[] generateHead(byte[] header) throws IOException {
		byte version = 0;
		if (this.header != null)
			version = this.header.getVersion();
		MapFileHeader fileHeader = new MapFileHeader(version, header);
		return fileHeader.toBytes();
	}

	public void save() throws IOException {
		save(this.file);
	}

	public void readPositionsOnly() throws IOException {
		readImages = false;
		readPositions = true;
		readContent();
		readImages = true;
	}

	public void readImagesOnly() throws IOException {
		readImages = true;
		readPositions = false;
		readContent();
		readPositions = true;
	}
	
	public boolean hasImages() throws IOException {
		readHeader();
		contentHeaders = header.getHeaders();
		System.out.println("Content Headers: " + contentHeaders.size());
		for (MapObjectHeader head : contentHeaders) {
			if (head.getType().equals(MapObjectType.MAP_IMAGE))
				return true;
		}
		return false;
		
	}

	public void readContent() throws IOException {
		readHeader();
		contentHeaders = header.getHeaders();
		System.out.println("Content Headers: " + contentHeaders.size());
		byte[] fileContent = Files.readAllBytes(file.toPath());
		for (MapObjectHeader head : contentHeaders) {
			long start = head.getContentStartIndex();
			long size = head.getContentSize();
			System.out.println("Header: " + start + " " + size);
			MapObject obj;
			byte[] content = ByteUtilsBE.substring(fileContent, (int) start, (int) size);
			switch (head.getType()) {
			case MAP_IMAGE:
				if (!readImages)
					break;
				obj = new MapImage(null);
				addMapObject(obj);
				((MapImage) obj).loadFromBytes(content);
				break;

			case MAP_POINTS:
				if (!readPositions)
					break;
				obj = new MapPositions(content);
				addMapObject(obj);
				break;
			default:
				obj = null;
				break;
			}
		}
		fullyReadContent = true;
	}

	public void addMapObjects(List<MapObject> list) {
		for (MapObject obj : list) {
			this.addMapObject(obj);
		}
	}

	private void readHeader() {
		if (file == null)
			throw new IllegalArgumentException("file == null !");
		try {
			header = new MapFileHeader(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public MapFile(String path) {
		this(new File(path));
	}
}
