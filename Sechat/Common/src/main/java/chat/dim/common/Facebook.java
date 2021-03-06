/* license: https://mit-license.org
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import chat.dim.AddressNameService;
import chat.dim.ID;
import chat.dim.Immortals;
import chat.dim.Meta;
import chat.dim.Profile;
import chat.dim.User;
import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.PrivateKey;
import chat.dim.crypto.SignKey;
import chat.dim.database.AddressNameTable;
import chat.dim.database.ContactTable;
import chat.dim.database.GroupTable;
import chat.dim.database.MetaTable;
import chat.dim.database.PrivateTable;
import chat.dim.database.ProfileTable;
import chat.dim.database.UserTable;
import chat.dim.protocol.NetworkType;

public class Facebook extends chat.dim.Facebook {
    public Facebook() {
        super();

        // ANS
        ans = new AddressNameService() {
            @Override
            public ID identifier(String name) {
                return ansTable.record(name);
            }

            @Override
            public boolean save(String name, ID identifier) {
                return ansTable.saveRecord(name, identifier);
            }
        };
        setANS(ans);
    }

    public static long EXPIRES = 3600;  // profile expires (1 hour)
    private static final String EXPIRES_KEY = "expires";

    private final AddressNameService ans;
    private Immortals immortals = new Immortals();

    private PrivateTable privateTable = new PrivateTable();
    private MetaTable metaTable = new MetaTable();
    private ProfileTable profileTable = new ProfileTable();

    private AddressNameTable ansTable = new AddressNameTable();

    private UserTable userTable = new UserTable();
    private GroupTable groupTable = new GroupTable();
    private ContactTable contactTable = new ContactTable();

    private List<User> users = null;

    //-------- Local Users

    @Override
    public List<User> getLocalUsers() {
        if (users == null) {
            users = new ArrayList<>();
            List<ID> list = userTable.allUsers();
            User user;
            for (ID item : list) {
                user = getUser(item);
                if (user == null) {
                    throw new NullPointerException("failed to get local user: " + item);
                }
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public User getCurrentUser() {
        return getUser(userTable.getCurrentUser());
    }

    public void setCurrentUser(User user) {
        users = null;
        userTable.setCurrentUser(user.identifier);
    }

    public boolean addUser(ID user) {
        users = null;
        return userTable.addUser(user);
    }

    public boolean removeUser(ID user) {
        users = null;
        return userTable.removeUser(user);
    }

    //-------- Contacts

    public boolean addContact(ID contact, ID user) {
        return contactTable.addContact(contact, user);
    }

    public boolean removeContact(ID contact, ID user) {
        return contactTable.removeContact(contact, user);
    }

    //-------- Private Key

    public boolean savePrivateKey(PrivateKey privateKey, ID identifier) {
        return privateTable.savePrivateKey(privateKey, identifier);
    }

    //-------- Meta

    @Override
    public boolean saveMeta(Meta meta, ID entity) {
        if (!verify(meta, entity)) {
            // meta not match ID
            return false;
        }
        return metaTable.saveMeta(meta, entity);
    }

    //-------- Profile

    @Override
    public boolean saveProfile(Profile profile) {
        if (!verify(profile)) {
            // profile's signature not match
            return false;
        }
        return profileTable.saveProfile(profile);
    }

    //-------- Relationship

    public boolean addMember(ID member, ID group) {
        return groupTable.addMember(member, group);
    }

    public boolean removeMember(ID member, ID group) {
        return groupTable.removeMember(member, group);
    }

    @Override
    public boolean saveMembers(List<ID> members, ID group) {
        return groupTable.saveMembers(members, group);
    }

    //--------

    public String getUsername(Object string) {
        return getUsername(getID(string));
    }

    public String getUsername(ID identifier) {
        String username = identifier.name;
        String nickname = getNickname(identifier);
        if (nickname != null && nickname.length() > 0) {
            if (identifier.isUser()) {
                if (username != null && username.length() > 0) {
                    return nickname + " (" + username + ")";
                }
            }
            return nickname;
        } else if (username != null && username.length() > 0) {
            return username;
        }
        // ID only contains address: BTC, ETH, ...
        return identifier.address.toString();
    }

    public String getNickname(ID identifier) {
        assert identifier.isUser();
        Profile profile = getProfile(identifier);
        return profile == null ? null : profile.getName();
    }

    public String getNumberString(ID identifier) {
        long number = identifier.getNumber();
        String string = String.format(Locale.CHINA, "%010d", number);
        string = string.substring(0, 3) + "-" + string.substring(3, 6) + "-" + string.substring(6);
        return string;
    }

    //-------- EntityDataSource

    @Override
    public Meta getMeta(ID identifier) {
        if (identifier.isBroadcast()) {
            // broadcast ID has not meta
            return null;
        }
        // try from database
        Meta meta = metaTable.getMeta(identifier);
        if (meta != null) {
            // is empty?
            if (meta.getKey() != null) {
                return meta;
            }
        }
        // try from immortals
        if (identifier.getType() == NetworkType.Main.value) {
            meta = immortals.getMeta(identifier);
            if (meta != null) {
                metaTable.saveMeta(meta, identifier);
                return meta;
            }
        }
        return null;
    }

    @Override
    public Profile getProfile(ID identifier) {
        // try from database
        Profile profile = profileTable.getProfile(identifier);
        if (profile != null) {
            // check expired time
            Date now = new Date();
            long timestamp = now.getTime() / 1000;
            Number expires = (Number) profile.get(EXPIRES_KEY);
            if (expires == null) {
                // set expired time
                profile.put(EXPIRES_KEY, timestamp + EXPIRES);
                // is empty?
                Set<String> names = profile.propertyNames();
                if (names != null && names.size() > 0) {
                    return profile;
                }
            } else if (expires.longValue() > timestamp) {
                // not expired yet
                return profile;
            }
        }
        // try from immortals
        if (identifier.getType() == NetworkType.Main.value) {
            Profile tai = immortals.getProfile(identifier);
            if (tai != null) {
                profileTable.saveProfile(tai);
                return tai;
            }
        }
        return null;
    }

    //-------- UserDataSource

    @Override
    public List<ID> getContacts(ID user) {
        List<ID> contacts = contactTable.getContacts(user);
        if (contacts == null || contacts.size() == 0) {
            // try immortals
            contacts = immortals.getContacts(user);
        }
        return contacts;
    }

    @Override
    public SignKey getPrivateKeyForSignature(ID user) {
        assert user.isUser() : "user ID error: " + user;
        SignKey key = privateTable.getPrivateKeyForSignature(user);
        if (key == null) {
            // try immortals
            key = immortals.getPrivateKeyForSignature(user);
        }
        return key;
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        assert user.isUser() : "user ID error: " + user;
        List<DecryptKey> keys = privateTable.getPrivateKeysForDecryption(user);
        if (keys == null || keys.size() == 0) {
            // try immortals
            keys = immortals.getPrivateKeysForDecryption(user);
            if (keys == null || keys.size() == 0) {
                // DIMP v1.0:
                //     decrypt key and the sign key are the same keys
                SignKey key = getPrivateKeyForSignature(user);
                if (key instanceof DecryptKey) {
                    keys = new ArrayList<>();
                    keys.add((DecryptKey) key);
                }
            }
        }
        return keys;
    }

    //-------- GroupDataSource

    @Override
    public ID getFounder(ID group) {
        // get from database
        ID founder = groupTable.getFounder(group);
        if (founder != null) {
            return founder;
        }
        return super.getFounder(group);
    }

    @Override
    public ID getOwner(ID group) {
        // get from database
        ID owner = groupTable.getOwner(group);
        if (owner != null) {
            return owner;
        }
        return super.getOwner(group);
    }

    @Override
    public List<ID> getMembers(ID group) {
        return groupTable.getMembers(group);
    }
}
