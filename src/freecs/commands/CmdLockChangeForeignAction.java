/**
 * Copyright (C) 2006  Rene M.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * Created on 16.10.2006
 */

package freecs.commands;

import java.util.Iterator;
import java.util.Vector;

import freecs.Server;
import freecs.interfaces.IActionStates;
import freecs.interfaces.ICommand;
import freecs.interfaces.IUserStates;
import freecs.util.GroupUserList;
import freecs.content.MessageState;
import freecs.core.User;

/**
 * @author Rene M.
 * 
 *         freecs.commands
 */
public class CmdLockChangeForeignAction extends AbstractCommand {
    private final String cmd = "/flock";
    private final String version = "1.0";
    private static final ICommand selve = new CmdLockChangeForeignAction();

    private CmdLockChangeForeignAction() {
    }

    public static ICommand getInstance() {
        return selve;
    }

    public Object instanceForSystem() {
        return this;
    }

    public String getCmd() {
        return cmd;
    }

    
    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    public boolean execute(MessageState msgState, String param) {

        if (isPunished(msgState)) {
            return false;
        }
        if (!msgState.sender.hasRight(IUserStates.ROLE_VIP)) {
            msgState.msgTemplate = "error.noRight.noVipAdmin";
            msgState.sender.sendMessage(msgState.mp);
            return false;
        }
        if (param == null || param.length() < 1) {
            msgState.msgTemplate = "error.flock.noArg";
            msgState.sender.sendMessage(msgState.mp);
            return false;
        }
        int pos = param.indexOf(" ");
        String action = param.substring(0, pos);
        param = param.substring(pos + 1);
        if (action.equals("col")) {
            return col(msgState, param);
        } else if (action.equals("away")) {
            return away(msgState, param);
        } else if (action.equals("me")) {
            return me(msgState, param);
        } else {
            return col(msgState, param);
        }

    }

    private boolean col(MessageState msgState, String param) {
        int pos = param.indexOf(" ");
        int pos2 = param.indexOf(":");
        if (pos == -1) {
            msgState.msgTemplate = "error.flock.noUser";
            msgState.sender.sendMessage(msgState.mp);
            return false;
        }

        msgState.param = param.substring(0, pos);
        String colcode = msgState.param;

        if (!isColorCodeValid(msgState, false)) {
            msgState.sender.sendMessage(msgState.mp);
            return false;
        }

        if (pos2 > 0) {
            msgState.reason = param.substring(pos2 + 1);
            msgState.message = param.substring(pos2 + 1);
            param = param.substring(0, pos2).trim();
        }
        param = param.substring(pos + 1);
        long lockTime = Server.srv.MAX_FLOCK_DURATION;
        if (lockTime == -1) {
            lockTime = 0;
        }
        long time = 0;
        pos = param.trim().lastIndexOf(" ");
        if (pos != -1) {
            String timeStrg = param.substring(pos).trim();
            long customTime = -1;
            try {
                customTime = Long.parseLong(timeStrg, 10);
                time = customTime;
                param = param.substring(0, pos).trim();
            } catch (NumberFormatException nfe) {
                /* last arg is not a number so it will be treated as a user */
            }
        }
        if (time > lockTime) {
            time = lockTime;
        }
        time = time * 60 * 1000;
        Vector<User> found = getMultibleUsers(msgState, param);
        GroupUserList gul = new GroupUserList();
        for (Iterator<User> i = found.iterator(); i.hasNext();) {
            User u = i.next();
            msgState.param = colcode;
            if (u.equals(msgState.sender)
                    || u.isCollocked) {
                i.remove();
                continue;
            } else if (isVip(u, u.getGroup(), msgState)
                    && !isVip(msgState.sender, u.getGroup(), msgState)) {
                msgState.msgTemplate = "error.noRight.noVipAdmin";
                msgState.sender.sendMessage(msgState.mp);
                i.remove();
                continue;
            } else if (!isVip(msgState.sender, u.getGroup(), msgState)
                    && !msgState.sender.hasRight(IUserStates.ROLE_GOD)) {
                msgState.targetGroup = u.getGroup();
                if (msgState.usrList.length > 1) {
                    msgState.msgTemplate = "error.membership.notMember.many";
                } else {
                    msgState.msgTemplate = "error.membership.notMember";
                }
                msgState.usercontext = u;
                msgState.sender.sendMessage(msgState.mp);
                i.remove();
                continue;
            }
            u.setColCode(msgState.param);
            u.setFadeColCode(null);
            if (time >0){
                Server.srv.storeUser(IActionStates.FLOCKCOL, u, msgState.reason,time, msgState.sender.getName());
                u.setCollock(true);
            }
            msgState.msgTemplate = "message.flock.personal";
            msgState.usrList = found.toArray();
            msgState.usercontext = u;
            if (found.size() > 1) {
                u.sendMessage(msgState.mp);
            }
            gul.addUser(u);
        }
        if (found.size() == 0) {
            return false;
        }
        if (found.size() == 1) {
            msgState.usercontext = found.get(0);
            msgState.msgTemplate = "message.flock.singular";
            msgState.targetGroup = msgState.usercontext.getGroup();
            if (msgState.targetGroup != null) {
                msgState.targetGroup.sendMessage(msgState.mp);
                if (!msgState.targetGroup.equals(msgState.sender.getGroup())) {
                    msgState.param = "/flockcol";
                    msgState.msgTemplate = "message.flock.confirm.singular";
                    msgState.sender.sendMessage(msgState.mp);
                }
            }
            return true;
        }
        msgState.usrList = found.toArray();
        msgState.param = "/flockcol";
        msgState.msgTemplate = "message.flock.confirm.plural";
        msgState.sender.sendMessage(msgState.mp);
        gul.sendMessage(msgState, "message.flock", true);
        return true;
    }

    private boolean away(MessageState msgState, String param) {
        int pos2 = param.indexOf(":");
        if (pos2 > 0) {
            msgState.reason = param.substring(pos2 + 1);
            msgState.message = param.substring(pos2 + 1);
            param = param.substring(0, pos2).trim();
        }

        long lockTime = Server.srv.MAX_FLOCK_DURATION;
        if (lockTime == -1) {
            lockTime = 0;
        }
        long time = 0;
        int pos = param.trim().lastIndexOf(" ");
        if (pos != -1) {
            String timeStrg = param.substring(pos).trim();
            long customTime = -1;
            try {
                customTime = Long.parseLong(timeStrg, 10);
                time = customTime;
                param = param.substring(0, pos).trim();
            } catch (NumberFormatException nfe) {
                /* last arg is not a number so it will be treated as a user */
            }
        }
        if (time > lockTime) {
            time = lockTime;
        }
        time = time * 60 * 1000;
        Vector<User> found = getMultibleUsers(msgState, param);
        GroupUserList gul = new GroupUserList();
        for (Iterator<User> i = found.iterator(); i.hasNext();) {
            User u = i.next();
            if (u.equals(msgState.sender)
                    || u.isAwaylocked) {
                i.remove();
                continue;
            } else if (isVip(u, u.getGroup(), msgState)
                    && !isVip(msgState.sender, u.getGroup(), msgState)) {
                msgState.msgTemplate = "error.noRight.noVipAdmin";
                msgState.sender.sendMessage(msgState.mp);
                i.remove();
                continue;
            } else if (!isVip(msgState.sender, u.getGroup(), msgState)
                    && !msgState.sender.hasRight(IUserStates.ROLE_GOD)) {
                msgState.targetGroup = u.getGroup();
                if (msgState.usrList.length > 1) {
                    msgState.msgTemplate = "error.membership.notMember.many";
                } else {
                    msgState.msgTemplate = "error.membership.notMember";
                }
                msgState.usercontext = u;
                msgState.sender.sendMessage(msgState.mp);
                i.remove();
                continue;
            }
            if (u.isAway()) {
                u.setAwayMessage(null);
            }
            if (time >0 ){
                Server.srv.storeUser(IActionStates.FLOCKAWAY, u, msgState.reason,time, msgState.sender.getName());
                u.setAwaylock(true);
            }
            msgState.msgTemplate = "message.flock.personal";
            msgState.usrList = found.toArray();
            msgState.usercontext = u;
            if (found.size() > 1) {
                u.sendMessage(msgState.mp);
            }
            gul.addUser(u);
        }
        if (found.size() == 0) {
            return false;
        }
        if (found.size() == 1) {
            msgState.usercontext = found.get(0);
            msgState.msgTemplate = "message.flock.singular";
            msgState.targetGroup = msgState.usercontext.getGroup();
            if (msgState.targetGroup != null) {
                msgState.targetGroup.sendMessage(msgState.mp);
                if (!msgState.targetGroup.equals(msgState.sender.getGroup())) {
                    msgState.param = "/flockaway";
                    msgState.msgTemplate = "message.flock.confirm.singular";
                    msgState.sender.sendMessage(msgState.mp);
                }
            }
            return true;
        }
        msgState.usrList = found.toArray();
        msgState.param = "/flockaway";
        msgState.msgTemplate = "message.flock.confirm.plural";
        msgState.sender.sendMessage(msgState.mp);
        gul.sendMessage(msgState, "message.flock", true);
        return true;

    }

    private boolean me(MessageState msgState, String param) {
        long lockTime = Server.srv.MAX_FLOCK_DURATION;
        if (lockTime == -1) {
            msgState.msgTemplate = "error.noRight.deactivated";
            msgState.sender.sendMessage (msgState.mp);
            Server.log(this, "maxFlockDuration == -1 ---> /flockme deactivated", Server.MSG_STATE, Server.LVL_VERBOSE);
            return false;
        }

        int pos2 = param.indexOf(":");
        if (pos2 > 0) {
            msgState.reason = param.substring(pos2 + 1);
            msgState.message = param.substring(pos2 + 1);
            param = param.substring(0, pos2).trim();
        }
        long time = 0;
        int pos = param.trim().lastIndexOf(" ");
        if (pos != -1) {
            String timeStrg = param.substring(pos).trim();
            long customTime = -1;
            try {
                customTime = Long.parseLong(timeStrg, 10);
                time = customTime;
                param = param.substring(0, pos).trim();
            } catch (NumberFormatException nfe) {
                /* last arg is not a number so it will be treated as a user */
            }
        }
        if (time > lockTime) {
            time = lockTime;
        }
        time = time * 60 * 1000;
        Vector<User> found = getMultibleUsers(msgState, param);
        GroupUserList gul = new GroupUserList();
        for (Iterator<User> i = found.iterator(); i.hasNext();) {
            User u = i.next();
            if (u.equals(msgState.sender)
                    || u.isMelocked) {
                i.remove();
                continue;
            } else if (isVip(u, u.getGroup(), msgState)
                    && !isVip(msgState.sender, u.getGroup(), msgState)) {
                msgState.msgTemplate = "error.noRight.noVipAdmin";
                msgState.sender.sendMessage(msgState.mp);
                i.remove();
                continue;
            } else if (!isVip(msgState.sender, u.getGroup(), msgState)
                    && !msgState.sender.hasRight(IUserStates.ROLE_GOD)) {
                msgState.targetGroup = u.getGroup();
                if (msgState.usrList.length > 1) {
                    msgState.msgTemplate = "error.membership.notMember.many";
                } else {
                    msgState.msgTemplate = "error.membership.notMember";
                }
                msgState.usercontext = u;
                msgState.sender.sendMessage(msgState.mp);
                i.remove();
                continue;
            }
            if (time >0 ){
                Server.srv.storeUser(IActionStates.FLOCKME, u, msgState.reason,time, msgState.sender.getName());
                u.setActlock(true);
            }
            msgState.msgTemplate = "message.flock.personal";
            msgState.usrList = found.toArray();
            msgState.usercontext = u;
            if (found.size() > 1) {
                u.sendMessage(msgState.mp);
            }
            gul.addUser(u);
        }
        if (found.size() == 0) {
            return false;
        }
        if (found.size() == 1) {
            msgState.usercontext = found.get(0);
            msgState.msgTemplate = "message.flock.singular";
            msgState.targetGroup = msgState.usercontext.getGroup();
            if (msgState.targetGroup != null) {
                msgState.targetGroup.sendMessage(msgState.mp);
                if (!msgState.targetGroup.equals(msgState.sender.getGroup())) {
                    msgState.param = "/flockme";
                    msgState.msgTemplate = "message.flock.confirm.singular";
                    msgState.sender.sendMessage(msgState.mp);
                }
            }
            return true;
        }
        msgState.usrList = found.toArray();
        msgState.param = "/flockme";
        msgState.msgTemplate = "message.flock.confirm.plural";
        msgState.sender.sendMessage(msgState.mp);
        gul.sendMessage(msgState, "message.flock", true);
        return true;
    }
}
