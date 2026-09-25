package fr.garfield.privatemines;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Parcelle {

    private final UUID owner;
    private final int centerX;
    private final int centerZ;
    private int size; // taille actuelle (10 au départ, agrandissable)
    private int maxInvited = 1; // nombre max d'invités (1 au départ, achetable jusqu'à 10)
    private String islandName = null; // nom personnalisé de l'Île (null = nom par défaut « Île de <pseudo> »)

    // Permissions des visiteurs (false = interdit par défaut)
    private boolean visitorsAllowed = false;  // la parcelle est-elle publique ?
    private boolean visitorsCanBreak  = false;
    private boolean visitorsCanPlace  = false;
    private boolean visitorsCanChests = false;
    private boolean visitorsCanInteract = false;
    private boolean visitorsCanPvp    = false;

    // Permissions des AMIS (globales : s'appliquent à tous les amis ajoutés)
    private boolean friendsCanDoors  = false;
    private boolean friendsCanBreak  = false;
    private boolean friendsCanPlace  = false;
    private boolean friendsCanChests = false;

    // Joueurs explicitement invités/amis (peuvent toujours entrer même si visitorsAllowed=false)
    private final Set<UUID> invited = new HashSet<>();

    // Banque d'île : quantité de fer et d'or déposés (en LINGOTS ; 1 bloc = 9 lingots).
    // Le niveau/score de l'île = fer×1 + or×3 (points). Voir ParcelleManager.islandPoints.
    private long ironDeposited = 0;
    private long goldDeposited = 0;

    // ===== OneBlock (« Le Bloc du Grand Appel ») =====
    // Phase courante (1..12), nombre de blocs cassés DANS la phase, et nombre de cycles complétés.
    private int  obPhase = 1;
    private int  obBlocs = 0;
    private int  obCycle = 0;
    // Les 2 numéros de bloc (dans la phase) où le bloc central devient un COFFRE (0 = non programmé).
    private int  obChest1 = 0;
    private int  obChest2 = 0;

    public Parcelle(UUID owner, int centerX, int centerZ, int size) {
        this.owner   = owner;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size    = size;
    }

    public UUID getOwner()   { return owner; }
    public int getCenterX()  { return centerX; }
    public int getCenterZ()  { return centerZ; }
    public int getSize()     { return size; }
    public void setSize(int size) { this.size = size; }

    public int getMaxInvited() { return maxInvited; }
    public void setMaxInvited(int maxInvited) { this.maxInvited = maxInvited; }

    public String getIslandName() { return islandName; }
    public void setIslandName(String islandName) { this.islandName = islandName; }

    public boolean isFriendsCanDoors()  { return friendsCanDoors; }
    public boolean isFriendsCanBreak()  { return friendsCanBreak; }
    public boolean isFriendsCanPlace()  { return friendsCanPlace; }
    public boolean isFriendsCanChests() { return friendsCanChests; }
    public void setFriendsCanDoors(boolean v)  { this.friendsCanDoors = v; }
    public void setFriendsCanBreak(boolean v)  { this.friendsCanBreak = v; }
    public void setFriendsCanPlace(boolean v)  { this.friendsCanPlace = v; }
    public void setFriendsCanChests(boolean v) { this.friendsCanChests = v; }

    // Vrai si uuid est un ami (invité explicite) du propriétaire.
    public boolean isFriend(UUID uuid) { return invited.contains(uuid); }

    public boolean isVisitorsAllowed()    { return visitorsAllowed; }
    public boolean isVisitorsCanBreak()   { return visitorsCanBreak; }
    public boolean isVisitorsCanPlace()   { return visitorsCanPlace; }
    public boolean isVisitorsCanChests()  { return visitorsCanChests; }
    public boolean isVisitorsCanInteract(){ return visitorsCanInteract; }
    public boolean isVisitorsCanPvp()     { return visitorsCanPvp; }

    public void setVisitorsAllowed(boolean v)    { this.visitorsAllowed = v; }
    public void setVisitorsCanBreak(boolean v)   { this.visitorsCanBreak = v; }
    public void setVisitorsCanPlace(boolean v)   { this.visitorsCanPlace = v; }
    public void setVisitorsCanChests(boolean v)  { this.visitorsCanChests = v; }
    public void setVisitorsCanInteract(boolean v){ this.visitorsCanInteract = v; }
    public void setVisitorsCanPvp(boolean v)     { this.visitorsCanPvp = v; }

    public Set<UUID> getInvited() { return invited; }

    // ===== Banque d'île (fer / or déposés, en lingots) =====
    public long getIronDeposited() { return ironDeposited; }
    public long getGoldDeposited() { return goldDeposited; }
    public void setIronDeposited(long v) { this.ironDeposited = Math.max(0, v); }
    public void setGoldDeposited(long v) { this.goldDeposited = Math.max(0, v); }
    public void addIron(long v) { setIronDeposited(this.ironDeposited + v); }
    public void addGold(long v) { setGoldDeposited(this.goldDeposited + v); }
    // Score de l'île en points : fer×1 + or×3.
    public long islandPoints() { return ironDeposited * 1L + goldDeposited * 3L; }

    // ===== OneBlock =====
    public int getObPhase() { return obPhase; }
    public int getObBlocs() { return obBlocs; }
    public int getObCycle() { return obCycle; }
    public void setObPhase(int v) { this.obPhase = Math.max(1, v); }
    public void setObBlocs(int v) { this.obBlocs = Math.max(0, v); }
    public void setObCycle(int v) { this.obCycle = Math.max(0, v); }
    public int getObChest1() { return obChest1; }
    public int getObChest2() { return obChest2; }
    public void setObChest1(int v) { this.obChest1 = Math.max(0, v); }
    public void setObChest2(int v) { this.obChest2 = Math.max(0, v); }

    public boolean canEnter(UUID uuid) {
        return uuid.equals(owner) || invited.contains(uuid) || visitorsAllowed;
    }

    // Vrai si la position (X/Z) est dans la parcelle.
    public boolean contains(int x, int z) {
        int half = size / 2;
        return x >= centerX - half && x <= centerX + half
            && z >= centerZ - half && z <= centerZ + half;
    }
}
