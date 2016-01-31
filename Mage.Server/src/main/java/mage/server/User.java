/*
 *  Copyright 2011 BetaSteward_at_googlemail.com. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice, this list of
 *        conditions and the following disclaimer.
 *
 *     2. Redistributions in binary form must reproduce the above copyright notice, this list
 *        of conditions and the following disclaimer in the documentation and/or other materials
 *        provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY BetaSteward_at_googlemail.com ``AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL BetaSteward_at_googlemail.com OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  The views and conclusions contained in the software and documentation are those of the
 *  authors and should not be interpreted as representing official policies, either expressed
 *  or implied, of BetaSteward_at_googlemail.com.
 */
package mage.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import mage.cards.decks.Deck;
import mage.constants.ManaType;
import mage.game.Table;
import mage.game.result.ResultProtos;
import mage.game.tournament.TournamentPlayer;
import mage.interfaces.callback.ClientCallback;
import mage.players.net.UserData;
import mage.server.draft.DraftSession;
import mage.server.game.GameManager;
import mage.server.game.GameSessionPlayer;
import mage.server.record.UserStats;
import mage.server.record.UserStatsRepository;
import mage.server.tournament.TournamentController;
import mage.server.tournament.TournamentManager;
import mage.server.tournament.TournamentSession;
import mage.server.util.SystemUtil;
import mage.view.TableClientMessage;
import org.apache.log4j.Logger;

/**
 *
 * @author BetaSteward_at_googlemail.com
 */
public class User {

    private static final Logger LOGGER = Logger.getLogger(User.class);

    public enum UserState {

        Created, Connected, Disconnected, Reconnected, Expired;
    }

    private final UUID userId;
    private final String userName;
    private final String host;
    private final Date connectionTime;
    private final Map<UUID, Table> tables;
    private final ArrayList<UUID> tablesToDelete;
    private final Map<UUID, GameSessionPlayer> gameSessions;
    private final Map<UUID, DraftSession> draftSessions;
    private final Map<UUID, UUID> userTournaments; // playerId, tournamentId
    private final Map<UUID, TournamentSession> constructing;
    private final Map<UUID, Deck> sideboarding;
    private final List<UUID> watchedGames;
    private String sessionId;
    private String pingInfo = "";
    private Date lastActivity;
    private UserState userState;
    private UserData userData;
    private UserStats userStats;

    public User(String userName, String host) {
        this.userId = UUID.randomUUID();
        this.userName = userName;
        this.host = host;
        this.userState = UserState.Created;

        this.connectionTime = new Date();
        this.lastActivity = new Date();

        this.tables = new ConcurrentHashMap<>();
        this.gameSessions = new ConcurrentHashMap<>();
        this.draftSessions = new ConcurrentHashMap<>();
        this.userTournaments = new ConcurrentHashMap<>();
        this.constructing = new ConcurrentHashMap<>();
        this.sideboarding = new ConcurrentHashMap<>();
        this.watchedGames = new ArrayList<>();
        this.tablesToDelete = new ArrayList<>();
        this.sessionId = "";
        this.userStats = null;
    }

    public String getName() {
        return userName;
    }

    public UUID getId() {
        return userId;
    }

    public String getHost() {
        return host;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        if (sessionId.isEmpty()) {
            userState = UserState.Disconnected;
            lostConnection();
            LOGGER.trace("USER - lost connection: " + userName + " id: " + userId);

        } else if (userState == UserState.Created) {
            userState = UserState.Connected;
            LOGGER.trace("USER - created: " + userName + " id: " + userId);
        } else {
            userState = UserState.Reconnected;
            reconnect();
            LOGGER.trace("USER - reconnected: " + userName + " id: " + userId);
        }
    }

    public void lostConnection() {
        // Because watched games don't get restored after reconnection call stop watching
        for (Iterator<UUID> iterator = watchedGames.iterator(); iterator.hasNext();) {
            UUID gameId = iterator.next();
            GameManager.getInstance().stopWatching(gameId, userId);
            iterator.remove();
        }
    }

    public boolean isConnected() {
        return userState.equals(UserState.Connected) || userState.equals(UserState.Reconnected);
    }

    public String getDisconnectDuration() {
        long secondsDisconnected = getSecondsDisconnected();
        long secondsLeft;
        String sign = "";
        if (secondsDisconnected > (3 * 60)) {
            sign = "-";
            secondsLeft = secondsDisconnected - (3 * 60);
        } else {
            secondsLeft = (3 * 60) - secondsDisconnected;
        }

        int minutes = (int) secondsLeft / 60;
        int seconds = (int) secondsLeft % 60;
        return new StringBuilder(sign).append(Integer.toString(minutes)).append(":").append(seconds > 9 ? seconds : "0" + Integer.toString(seconds)).toString();
    }

    public long getSecondsDisconnected() {
        return SystemUtil.getDateDiff(lastActivity, new Date(), TimeUnit.SECONDS);
    }

    public Date getConnectionTime() {
        return connectionTime;
    }

    public void fireCallback(final ClientCallback call) {
        if (isConnected()) {
            Session session = SessionManager.getInstance().getSession(sessionId);
            if (session != null) {
                session.fireCallback(call);
            }
        }
    }

    public void ccJoinedTable(final UUID roomId, final UUID tableId, boolean isTournament) {
        fireCallback(new ClientCallback("joinedTable", tableId, new TableClientMessage(roomId, tableId, isTournament)));
    }

    public void ccGameStarted(final UUID gameId, final UUID playerId) {
        fireCallback(new ClientCallback("startGame", gameId, new TableClientMessage(gameId, playerId)));
    }

    public void ccDraftStarted(final UUID draftId, final UUID playerId) {
        fireCallback(new ClientCallback("startDraft", draftId, new TableClientMessage(draftId, playerId)));
    }

    public void ccTournamentStarted(final UUID tournamentId, final UUID playerId) {
        fireCallback(new ClientCallback("startTournament", tournamentId, new TableClientMessage(tournamentId, playerId)));
    }

    public void ccSideboard(final Deck deck, final UUID tableId, final int time, boolean limited) {
        fireCallback(new ClientCallback("sideboard", tableId, new TableClientMessage(deck, tableId, time, limited)));
        sideboarding.put(tableId, deck);
    }

    public void ccConstruct(final Deck deck, final UUID tableId, final int time) {
        fireCallback(new ClientCallback("construct", tableId, new TableClientMessage(deck, tableId, time)));
    }

    public void ccShowTournament(final UUID tournamentId) {
        fireCallback(new ClientCallback("showTournament", tournamentId));
    }

    public void ccShowGameEndDialog(final UUID gameId) {
        fireCallback(new ClientCallback("showGameEndDialog", gameId));
    }

    public void showUserMessage(final String titel, String message) {
        List<String> messageData = new LinkedList<>();
        messageData.add(titel);
        messageData.add(message);
        fireCallback(new ClientCallback("showUserMessage", null, messageData));
    }

    public boolean ccWatchGame(final UUID gameId) {
        fireCallback(new ClientCallback("watchGame", gameId));
        return true;
    }

    public void ccReplayGame(final UUID gameId) {
        fireCallback(new ClientCallback("replayGame", gameId));
    }

    public void sendPlayerUUID(final UUID gameId, final UUID data) {
        lastActivity = new Date();
        GameManager.getInstance().sendPlayerUUID(gameId, userId, data);
    }

    public void sendPlayerString(final UUID gameId, final String data) {
        lastActivity = new Date();
        GameManager.getInstance().sendPlayerString(gameId, userId, data);
    }

    public void sendPlayerManaType(final UUID gameId, final UUID playerId, final ManaType data) {
        lastActivity = new Date();
        GameManager.getInstance().sendPlayerManaType(gameId, playerId, userId, data);
    }

    public void sendPlayerBoolean(final UUID gameId, final Boolean data) {
        lastActivity = new Date();
        GameManager.getInstance().sendPlayerBoolean(gameId, userId, data);
    }

    public void sendPlayerInteger(final UUID gameId, final Integer data) {
        lastActivity = new Date();
        GameManager.getInstance().sendPlayerInteger(gameId, userId, data);
    }

    public void updateLastActivity(String pingInfo) {
        if (pingInfo != null) {
            this.pingInfo = pingInfo;
        }
        lastActivity = new Date();
        if (userState == UserState.Disconnected) { // this can happen if user reconnects very fast after disconnect
            userState = UserState.Reconnected;
        }
    }

    public boolean isExpired(Date expired) {
        if (lastActivity.before(expired)) {
            LOGGER.trace(userName + " is expired!");
            userState = UserState.Expired;
            return true;
        }
        LOGGER.trace(new StringBuilder("isExpired: User ").append(userName).append(" lastActivity: ").append(lastActivity).append(" expired: ").append(expired).toString());
        return false;
        /*userState == UserState.Disconnected && */

    }

    private void reconnect() {
        for (Entry<UUID, Table> entry : tables.entrySet()) {
            ccJoinedTable(entry.getValue().getRoomId(), entry.getValue().getId(), entry.getValue().isTournament());
        }
        for (Entry<UUID, UUID> entry : userTournaments.entrySet()) {
            TournamentController tournamentController = TournamentManager.getInstance().getTournamentController(entry.getValue());
            if (tournamentController != null) {
                ccTournamentStarted(entry.getValue(), entry.getKey());
                tournamentController.rejoin(entry.getKey());
            }
        }

        for (Entry<UUID, GameSessionPlayer> entry : gameSessions.entrySet()) {
            ccGameStarted(entry.getValue().getGameId(), entry.getKey());
            entry.getValue().init();
            GameManager.getInstance().sendPlayerString(entry.getValue().getGameId(), userId, "");
        }

        for (Entry<UUID, DraftSession> entry : draftSessions.entrySet()) {
            ccDraftStarted(entry.getValue().getDraftId(), entry.getKey());
            entry.getValue().init();
            entry.getValue().update();
        }

        for (Entry<UUID, TournamentSession> entry : constructing.entrySet()) {
            entry.getValue().construct(0); // TODO: Check if this is correct
        }
        for (Entry<UUID, Deck> entry : sideboarding.entrySet()) {
            TableController controller = TableManager.getInstance().getController(entry.getKey());
            ccSideboard(entry.getValue(), entry.getKey(), controller.getRemainingTime(), controller.getOptions().isLimited());
        }
    }

    public void addGame(UUID playerId, GameSessionPlayer gameSession) {
        gameSessions.put(playerId, gameSession);
    }

    public void removeGame(UUID playerId) {
        gameSessions.remove(playerId);
    }

    public void addDraft(UUID playerId, DraftSession draftSession) {
        draftSessions.put(playerId, draftSession);
    }

    public void removeDraft(UUID playerId) {
        draftSessions.remove(playerId);
    }

    public void addTournament(UUID playerId, UUID tournamentId) {
        userTournaments.put(playerId, tournamentId);
    }

    public void removeTournament(UUID playerId) {
        userTournaments.remove(playerId);
    }

    public void addTable(UUID playerId, Table table) {
        tables.put(playerId, table);
    }

    public void removeTable(UUID playerId) {
        tables.remove(playerId);
    }

    public void addConstructing(UUID playerId, TournamentSession tournamentSession) {
        constructing.put(playerId, tournamentSession);
    }

    public void removeConstructing(UUID playerId) {
        constructing.remove(playerId);
    }

    public void removeSideboarding(UUID tableId) {
        sideboarding.remove(tableId);
    }

    public void remove(DisconnectReason reason) {
        LOGGER.trace("REMOVE " + getName() + " Draft sessions " + draftSessions.size());
        for (DraftSession draftSession : draftSessions.values()) {
            draftSession.setKilled();
        }
        draftSessions.clear();
        LOGGER.trace("REMOVE " + getName() + " Tournament sessions " + userTournaments.size());
        for (UUID tournamentId : userTournaments.values()) {
            TournamentManager.getInstance().quit(tournamentId, getId());
        }
        userTournaments.clear();
        LOGGER.trace("REMOVE " + getName() + " Tables " + tables.size());
        for (Entry<UUID, Table> entry : tables.entrySet()) {
            LOGGER.debug("-- leave tableId: " + entry.getValue().getId());
            TableManager.getInstance().leaveTable(userId, entry.getValue().getId());
        }
        tables.clear();
        LOGGER.trace("REMOVE " + getName() + " Game sessions: " + gameSessions.size());
        for (GameSessionPlayer gameSessionPlayer : gameSessions.values()) {
            LOGGER.debug("-- kill game session of gameId: " + gameSessionPlayer.getGameId());
            GameManager.getInstance().quitMatch(gameSessionPlayer.getGameId(), userId);
            gameSessionPlayer.quitGame();
        }
        gameSessions.clear();
        LOGGER.trace("REMOVE " + getName() + " watched Games " + watchedGames.size());
        for (UUID gameId : watchedGames) {
            GameManager.getInstance().stopWatching(gameId, userId);
        }
        watchedGames.clear();
        LOGGER.trace("REMOVE " + getName() + " Chats ");
        ChatManager.getInstance().removeUser(userId, reason);
    }

    public void setUserData(UserData userData) {
        if (this.userData != null) {
            this.userData.update(userData);
        } else {
            this.userData = userData;
            resetUserStats();
        }
    }

    public UserData getUserData() {
        if (userData == null) {// default these to avaiod NPE -> will be updated from client short after
            return UserData.getDefaultUserDataView();
        }
        return this.userData;
    }

    public String getGameInfo() {
        StringBuilder sb = new StringBuilder();

        int draft = 0, match = 0, sideboard = 0, tournament = 0, construct = 0, waiting = 0;

        for (Map.Entry<UUID, Table> tableEntry : tables.entrySet()) {
            if (tableEntry != null) {
                Table table = tableEntry.getValue();
                if (table != null) {
                    if (table.isTournament()) {
                        if (tableEntry.getKey() != null) {
                            TournamentPlayer tournamentPlayer = table.getTournament().getPlayer(tableEntry.getKey());
                            if (tournamentPlayer != null) {
                                if (!tournamentPlayer.isEliminated()) {
                                    switch (table.getState()) {
                                        case WAITING:
                                        case STARTING:
                                        case READY_TO_START:
                                            waiting++;
                                            break;
                                        case CONSTRUCTING:
                                            construct++;
                                            break;
                                        case DRAFTING:
                                            draft++;
                                            break;
                                        case DUELING:
                                            tournament++;
                                            break;
                                    }

                                    if (!isConnected()) {
                                        tournamentPlayer.setDisconnectInfo(" (discon. " + getDisconnectDuration() + ")");
                                    } else {
                                        tournamentPlayer.setDisconnectInfo("");
                                    }
                                }
                            } else {
                                // can happen if tournamet has just ended
                                LOGGER.debug(getName() + " tournament player missing - tableId:" + table.getId(), null);
                                tablesToDelete.add(tableEntry.getKey());
                            }
                        } else {
                            LOGGER.error(getName() + " tournament key missing - tableId: " + table.getId(), null);
                        }
                    } else {
                        switch (table.getState()) {
                            case WAITING:
                            case STARTING:
                            case READY_TO_START:
                                waiting++;
                                break;
                            case SIDEBOARDING:
                                sideboard++;
                                break;
                            case DUELING:
                                match++;
                                break;
                        }
                    }
                }
            }
        }
        if (!tablesToDelete.isEmpty()) {
            for (UUID keyId : tablesToDelete) {
                removeTable(keyId);
            }
            tablesToDelete.clear();
        }
        if (waiting > 0) {
            sb.append("Wait: ").append(waiting).append(" ");
        }
        if (match > 0) {
            sb.append("Match: ").append(match).append(" ");
        }
        if (sideboard > 0) {
            sb.append("Sideb: ").append(sideboard).append(" ");
        }
        if (draft > 0) {
            sb.append("Draft: ").append(draft).append(" ");
        }
        if (construct > 0) {
            sb.append("Const: ").append(construct).append(" ");
        }
        if (tournament > 0) {
            sb.append("Tourn: ").append(tournament).append(" ");
        }
        if (watchedGames.size() > 0) {
            sb.append("Watch: ").append(watchedGames.size()).append(" ");
        }
        return sb.toString();
    }

    public void addGameWatchInfo(UUID gameId) {
        watchedGames.add(gameId);
    }

    public void removeGameWatchInfo(UUID gameId) {
        watchedGames.remove(gameId);
    }

    public UserState getUserState() {
        return userState;
    }

    public String getPingInfo() {
        if (isConnected()) {
            return pingInfo;
        } else {
            return " (discon. " + getDisconnectDuration() + ")";
        }
    }

    public void resetUserStats() {
        if (userData == null) {
            return;
        }
        userStats = UserStatsRepository.instance.getUser(this.userName);
        if (userStats != null) {
            userData.setMatchHistory(userStatsToMatchHistory(userStats.getProto()));
            userData.setTourneyHistory(userStatsToTourneyHistory(userStats.getProto()));
        } else {
            userData.setMatchHistory("0");
            userData.setTourneyHistory("0");
        }
    }

    public String getMatchHistory() {
        if (userData != null) {
            return userData.getMatchHistory();
        }
        return "<not available>";
    }

    public String getTourneyHistory() {
        if (userData != null) {
            return userData.getTourneyHistory();
        }
        return "<not available>";
    }

    public static String userStatsToHistory(ResultProtos.UserStatsProto proto) {
        return "Matches:" + userStatsToMatchHistory(proto) +
                " Tourneys: " + userStatsToTourneyHistory(proto);
    }

    public static String userStatsToMatchHistory(ResultProtos.UserStatsProto proto) {
        StringBuilder builder = new StringBuilder();
        builder.append(proto.getMatches());
        List<String> quit = new ArrayList<>();
        if (proto.getMatchesIdleTimeout() > 0) {
            quit.add("I:" + Integer.toString(proto.getMatchesIdleTimeout()));
        }
        if (proto.getMatchesTimerTimeout() > 0) {
            quit.add("T:" + Integer.toString(proto.getMatchesTimerTimeout()));
        }
        if (proto.getMatchesQuit() > 0) {
            quit.add("Q:" + Integer.toString(proto.getMatchesQuit()));
        }
        if (quit.size() > 0) {
            builder.append(" (");
            joinStrings(builder, quit, " ");
            builder.append(")");
        }
        return builder.toString();
    }

    public static String userStatsToTourneyHistory(ResultProtos.UserStatsProto proto) {
        StringBuilder builder = new StringBuilder();
        builder.append(proto.getTourneys());
        List<String> quit = new ArrayList<>();
        if (proto.getTourneysQuitDuringDrafting() > 0) {
            quit.add("D:" + Integer.toString(proto.getTourneysQuitDuringDrafting()));
        }
        if (proto.getTourneysQuitDuringConstruction() > 0) {
            quit.add("C:" + Integer.toString(proto.getTourneysQuitDuringConstruction()));
        }
        if (proto.getTourneysQuitDuringRound() > 0) {
            quit.add("R:" + Integer.toString(proto.getTourneysQuitDuringRound()));
        }
        if (quit.size() > 0) {
            builder.append(" (");
            joinStrings(builder, quit, " ");
            builder.append(")");
        }
        return builder.toString();
    }

    private static void joinStrings(StringBuilder joined, List<String> strings, String separator) {
        for (int i = 0; i < strings.size(); ++i) {
            if (i > 0) {
                joined.append(separator);
            }
            joined.append(strings.get(i));
        }
    }

}
