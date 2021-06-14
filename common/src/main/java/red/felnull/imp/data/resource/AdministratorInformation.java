package red.felnull.imp.data.resource;

import net.minecraft.nbt.CompoundTag;
import red.felnull.imp.util.NbtUtils;
import red.felnull.otyacraftengine.data.ITAGSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdministratorInformation implements ITAGSerializable {
    private Map<UUID, AuthorityType> adminData = new HashMap<>();

    public AdministratorInformation(CompoundTag tag) {
        this.load(tag);
    }

    public AdministratorInformation(Map<UUID, AuthorityType> adminData) {
        this.adminData = adminData;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        NbtUtils.writeAdminData(tag, "AdminData", adminData);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        NbtUtils.readAdminData(tag, "AdminData", adminData);
    }

    public Map<UUID, AuthorityType> getAdminData() {
        return adminData;
    }

    public AuthorityType getAuthority(UUID plId) {
        if (adminData.containsKey(plId))
            return adminData.get(plId);

        return AuthorityType.READ_ONLY;
    }

    public static enum AuthorityType {
        OWNER("owner", true, true, true),
        ADMINISTRATOR("administrator", false, true, true),
        READ_ONLY("read_only", false, true, false),
        BAN("ban", false, false, false);

        private final String name;
        private final boolean owner;
        private final boolean read;
        private final boolean save;

        private AuthorityType(String name, boolean owner, boolean read, boolean save) {
            this.name = name;
            this.owner = owner;
            this.read = read;
            this.save = save;
        }

        public String getNmae() {
            return name;
        }

        public boolean canRead() {
            return read;
        }

        public boolean canSave() {
            return save;
        }

        public boolean isOwner() {
            return owner;
        }

        public static AuthorityType getAuthorityTypeByName(String name) {
            for (AuthorityType it : values()) {
                if (it.getNmae().equals(name))
                    return it;
            }
            return OWNER;
        }
    }
}
