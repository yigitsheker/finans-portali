import { useState, useEffect } from 'react';
import { watchlistApi } from '../api/watchlistApi';
import { getMarketSummary } from '../api/portfolioApi';
import { useI18n } from '../contexts/I18nContext';
import notify from '../utils/notify';
import Modal from './Modal';
import FinexStyleMarket from './FinexStyleMarket';

const WatchlistManager = ({ keycloak }) => {
  const { t } = useI18n();
  const [watchlists, setWatchlists] = useState([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState(null);
  const [instruments, setInstruments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newWatchlistName, setNewWatchlistName] = useState('');
  const [showRenameModal, setShowRenameModal] = useState(false);
  const [renameWatchlistName, setRenameWatchlistName] = useState('');
  // Pending delete target — drives a styled confirm modal instead of window.confirm.
  const [deleteTarget, setDeleteTarget] = useState(null);

  useEffect(() => {
    loadWatchlists();
    loadInstruments();
  }, []);

  const loadWatchlists = async () => {
    try {
      const data = await watchlistApi.getWatchlists(keycloak);
      setWatchlists(data);
      if (data.length > 0 && !selectedWatchlist) {
        setSelectedWatchlist(data[0]);
      }
    } catch (error) {
      console.error('Failed to load watchlists:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadInstruments = async () => {
    try {
      const data = await getMarketSummary();
      setInstruments(data);
    } catch (error) {
      console.error('Failed to load instruments:', error);
    }
  };

  const handleCreateWatchlist = async () => {
    if (!newWatchlistName.trim()) return;
    try {
      const newWatchlist = await watchlistApi.createWatchlist(keycloak, { name: newWatchlistName });
      setWatchlists([newWatchlist, ...watchlists]);
      setSelectedWatchlist(newWatchlist);
      setNewWatchlistName('');
      setShowCreateModal(false);
    } catch (error) {
      console.error('Failed to create watchlist:', error);
      notify(t("watchlist.createFailed"), { variant: "error" });
    }
  };

  const handleRenameWatchlist = async () => {
    if (!selectedWatchlist || !renameWatchlistName.trim()) return;
    try {
      const updated = await watchlistApi.updateWatchlist(keycloak, selectedWatchlist.id, {
        name: renameWatchlistName
      });
      setWatchlists(watchlists.map(w => w.id === updated.id ? updated : w));
      setSelectedWatchlist(updated);
      setRenameWatchlistName('');
      setShowRenameModal(false);
    } catch (error) {
      console.error('Failed to rename watchlist:', error);
      notify(t("watchlist.renameFailed"), { variant: "error" });
    }
  };

  const confirmDeleteWatchlist = async () => {
    if (!deleteTarget) return;
    const id = deleteTarget.id;
    try {
      await watchlistApi.deleteWatchlist(keycloak, id);
      const newWatchlists = watchlists.filter(w => w.id !== id);
      setWatchlists(newWatchlists);
      if (selectedWatchlist?.id === id) {
        setSelectedWatchlist(newWatchlists[0] || null);
      }
      setDeleteTarget(null);
    } catch (error) {
      console.error('Failed to delete watchlist:', error);
      notify(t("watchlist.deleteFailed"), { variant: "error" });
    }
  };

  const handleAddToWatchlist = async (symbol) => {
    if (!selectedWatchlist) {
      notify(t("watchlist.selectFirst"), { variant: "warning" });
      return;
    }
    try {
      await watchlistApi.addToWatchlist(keycloak, {
        watchlistId: selectedWatchlist.id,
        symbol
      });
      const updated = await watchlistApi.getWatchlist(keycloak, selectedWatchlist.id);
      setWatchlists(watchlists.map(w => w.id === updated.id ? updated : w));
      setSelectedWatchlist(updated);
    } catch (error) {
      console.error('Failed to add to watchlist:', error);
      notify(t("watchlist.addFailed"), { variant: "error" });
    }
  };

  const handleRemoveFromWatchlist = async (symbol) => {
    if (!selectedWatchlist) return;
    try {
      await watchlistApi.removeFromWatchlist(keycloak, selectedWatchlist.id, symbol);
      const updated = await watchlistApi.getWatchlist(keycloak, selectedWatchlist.id);
      setWatchlists(watchlists.map(w => w.id === updated.id ? updated : w));
      setSelectedWatchlist(updated);
    } catch (error) {
      console.error('Failed to remove from watchlist:', error);
      notify(t("watchlist.removeFailed"), { variant: "error" });
    }
  };

  const filteredInstruments = selectedWatchlist
    ? instruments.filter(inst => selectedWatchlist.symbols.includes(inst.symbol))
    : [];

  if (loading) {
    return <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>{t("common.loading")}</div>;
  }

  return (
    <div style={{ padding: '20px' }}>
      {/* Watchlist Tabs */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '20px', flexWrap: 'wrap', alignItems: 'center' }}>
        {watchlists.map(watchlist => (
          <div key={watchlist.id} style={{ position: 'relative' }}>
            <button
              onClick={() => setSelectedWatchlist(watchlist)}
              style={{
                padding: '10px 40px 10px 20px',
                border: selectedWatchlist?.id === watchlist.id ? '2px solid var(--accent-solid)' : '1px solid var(--border)',
                borderRadius: '8px',
                background: selectedWatchlist?.id === watchlist.id ? 'var(--accent)' : 'var(--bg-card)',
                cursor: 'pointer',
                fontWeight: selectedWatchlist?.id === watchlist.id ? 'bold' : 'normal',
                color: selectedWatchlist?.id === watchlist.id ? 'var(--accent-solid)' : 'var(--text-primary)',
                transition: 'all 0.2s'
              }}
            >
              {watchlist.name} ({watchlist.symbols.length})
            </button>
            {selectedWatchlist?.id === watchlist.id && (
              <div style={{ position: 'absolute', right: '5px', top: '50%', transform: 'translateY(-50%)', display: 'flex', gap: '5px' }}>
                <button
                  onClick={(e) => { e.stopPropagation(); setRenameWatchlistName(watchlist.name); setShowRenameModal(true); }}
                  style={{ padding: '2px 6px', border: 'none', background: 'transparent', cursor: 'pointer', fontSize: '14px', opacity: 0.7 }}
                  title={t("watchlist.rename")}
                >
                  ✏️
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); setDeleteTarget(watchlist); }}
                  style={{ padding: '2px 6px', border: 'none', background: 'transparent', cursor: 'pointer', fontSize: '14px', color: 'var(--red)', opacity: 0.7 }}
                  title={t("watchlist.delete")}
                >
                  🗑️
                </button>
              </div>
            )}
          </div>
        ))}

        <button
          onClick={() => setShowCreateModal(true)}
          style={{
            padding: '10px 20px', border: '2px dashed var(--accent-solid)', borderRadius: '8px',
            background: 'var(--bg-card)', cursor: 'pointer', color: 'var(--accent-solid)', fontWeight: 'bold', transition: 'all 0.2s'
          }}
        >
          {t("watchlist.newList")}
        </button>
      </div>

      {/* Create Watchlist Modal */}
      <Modal
        open={showCreateModal}
        title={t("watchlist.createTitle")}
        onClose={() => { setShowCreateModal(false); setNewWatchlistName(''); }}
        footer={
          <>
            <button style={dialogBtnGhost} onClick={() => { setShowCreateModal(false); setNewWatchlistName(''); }}>{t("common.cancel")}</button>
            <button style={dialogBtnPrimary} onClick={handleCreateWatchlist}>{t("watchlist.create")}</button>
          </>
        }
      >
        <input
          type="text"
          value={newWatchlistName}
          onChange={(e) => setNewWatchlistName(e.target.value)}
          placeholder={t("watchlist.listNamePh")}
          style={dialogInput}
          onKeyDown={(e) => e.key === 'Enter' && handleCreateWatchlist()}
          autoFocus
        />
      </Modal>

      {/* Rename Watchlist Modal */}
      <Modal
        open={showRenameModal}
        title={t("watchlist.renameTitle")}
        onClose={() => { setShowRenameModal(false); setRenameWatchlistName(''); }}
        footer={
          <>
            <button style={dialogBtnGhost} onClick={() => { setShowRenameModal(false); setRenameWatchlistName(''); }}>{t("common.cancel")}</button>
            <button style={dialogBtnPrimary} onClick={handleRenameWatchlist}>{t("common.save")}</button>
          </>
        }
      >
        <input
          type="text"
          value={renameWatchlistName}
          onChange={(e) => setRenameWatchlistName(e.target.value)}
          placeholder={t("watchlist.newListNamePh")}
          style={dialogInput}
          onKeyDown={(e) => e.key === 'Enter' && handleRenameWatchlist()}
          autoFocus
        />
      </Modal>

      {/* Delete confirm Modal */}
      <Modal
        open={!!deleteTarget}
        title={t("watchlist.delete")}
        onClose={() => setDeleteTarget(null)}
        footer={
          <>
            <button style={dialogBtnGhost} onClick={() => setDeleteTarget(null)}>{t("common.cancel")}</button>
            <button style={dialogBtnDanger} onClick={confirmDeleteWatchlist}>{t("watchlist.delete")}</button>
          </>
        }
      >
        <p style={{ margin: 0, color: 'var(--text-primary)', fontSize: 14 }}>{t("watchlist.deleteConfirm")}</p>
      </Modal>

      {/* Watchlist Content */}
      {selectedWatchlist ? (
        filteredInstruments.length > 0 ? (
          <FinexStyleMarket
            keycloak={keycloak}
            embedded
            instruments={filteredInstruments}
            onAddToWatchlist={handleAddToWatchlist}
            onRemoveFromWatchlist={handleRemoveFromWatchlist}
            watchlistSymbols={selectedWatchlist.symbols}
          />
        ) : (
          <div style={{ padding: '40px', textAlign: 'center', background: 'var(--bg-card)', borderRadius: '12px', border: '2px dashed var(--border)' }}>
            <p style={{ fontSize: '18px', color: 'var(--text-muted)', marginBottom: '10px' }}>{t("watchlist.emptyListTitle")}</p>
            <p style={{ fontSize: '14px', color: 'var(--text-muted)' }}>{t("watchlist.emptyListSub")}</p>
          </div>
        )
      ) : (
        <div style={{ padding: '40px', textAlign: 'center', background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border)' }}>
          <p style={{ fontSize: '18px', color: 'var(--text-muted)' }}>{t("watchlist.noSelection")}</p>
        </div>
      )}
    </div>
  );
};

const dialogInput = {
  width: '100%', padding: '10px', border: '1px solid var(--input-border)', borderRadius: '6px',
  fontSize: '14px', background: 'var(--input-bg)', color: 'var(--text-primary)', boxSizing: 'border-box',
};
const dialogBtnGhost = {
  padding: '9px 16px', border: '1px solid var(--border-card)', borderRadius: '8px',
  background: 'transparent', cursor: 'pointer', color: 'var(--text-primary)', fontWeight: 600,
};
const dialogBtnPrimary = {
  padding: '9px 16px', border: 'none', borderRadius: '8px',
  background: 'var(--accent-solid)', color: '#fff', cursor: 'pointer', fontWeight: 600,
};
const dialogBtnDanger = {
  padding: '9px 16px', border: 'none', borderRadius: '8px',
  background: 'var(--red)', color: '#fff', cursor: 'pointer', fontWeight: 600,
};

export default WatchlistManager;
