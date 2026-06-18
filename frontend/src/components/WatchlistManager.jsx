import { useState, useEffect } from 'react';
import { IconEdit, IconTrash, IconCheck } from './common/icons';
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
  // Add-instrument modal: search any instrument (all asset types) and add it to
  // the selected list directly from this page.
  const [showAddModal, setShowAddModal] = useState(false);
  const [addSearch, setAddSearch] = useState('');
  const [addType, setAddType] = useState('ALL');

  // Map an instrument type to a localized badge/chip label. STOCK+BIST share the
  // "stocks" label so the user sees one category, not two.
  const typeLabel = (type) => {
    switch (type) {
      case 'STOCK':
      case 'BIST': return t('nav.stocks');
      case 'CRYPTO': return t('nav.crypto');
      case 'FUND': return t('nav.funds');
      case 'BOND': return t('nav.bonds');
      case 'FX': return t('nav.fx');
      case 'COMMODITY': return t('nav.commodities');
      case 'VIOP': return t('nav.viop');
      case 'INDEX': return t('watchlist.typeIndex');
      default: return type;
    }
  };

  // Category chips for the add modal. Each maps to one or more raw types.
  const typeGroups = [
    { key: 'STOCK', types: ['STOCK', 'BIST'], label: t('nav.stocks') },
    { key: 'CRYPTO', types: ['CRYPTO'], label: t('nav.crypto') },
    { key: 'FUND', types: ['FUND'], label: t('nav.funds') },
    { key: 'BOND', types: ['BOND'], label: t('nav.bonds') },
    { key: 'FX', types: ['FX'], label: t('nav.fx') },
    { key: 'COMMODITY', types: ['COMMODITY'], label: t('nav.commodities') },
    { key: 'VIOP', types: ['VIOP'], label: t('nav.viop') },
    { key: 'INDEX', types: ['INDEX'], label: t('watchlist.typeIndex') },
  ];

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

  const openAddModal = () => {
    if (!selectedWatchlist) {
      notify(t("watchlist.selectFirst"), { variant: "warning" });
      return;
    }
    setAddSearch('');
    setAddType('ALL');
    setShowAddModal(true);
  };

  // Instruments offered in the add modal — every asset type, filtered by the
  // active category chip and the search query (symbol or name).
  const addCandidates = instruments.filter(inst => {
    if (addType !== 'ALL') {
      const grp = typeGroups.find(g => g.key === addType);
      if (grp && !grp.types.includes(inst.type)) return false;
    }
    const q = addSearch.trim().toLowerCase();
    if (q) {
      const sym = String(inst.symbol || '').toLowerCase();
      const name = String(inst.name || '').toLowerCase();
      if (!sym.includes(q) && !name.includes(q)) return false;
    }
    return true;
  });
  const addedSymbols = new Set(selectedWatchlist?.symbols ?? []);

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
                // Reserve room on the right ONLY when selected, since that's
                // when the rename/delete icon overlay appears. Otherwise the
                // icons overlapped the name+count text ("göt (1✎🗑").
                padding: selectedWatchlist?.id === watchlist.id ? '10px 66px 10px 18px' : '10px 18px',
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
                  <IconEdit size={14} />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); setDeleteTarget(watchlist); }}
                  style={{ padding: '2px 6px', border: 'none', background: 'transparent', cursor: 'pointer', fontSize: '14px', color: 'var(--red)', opacity: 0.7 }}
                  title={t("watchlist.delete")}
                >
                  <IconTrash size={14} />
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

        {selectedWatchlist && (
          <button
            onClick={openAddModal}
            style={{
              padding: '10px 20px', border: 'none', borderRadius: '8px',
              background: 'var(--accent-solid)', color: '#fff', cursor: 'pointer', fontWeight: 'bold', transition: 'all 0.2s'
            }}
          >
            + {t("watchlist.addInstrument")}
          </button>
        )}
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

      {/* Add-instrument Modal — search across ALL asset types */}
      <Modal
        open={showAddModal}
        title={`${t("watchlist.addModalTitle")}${selectedWatchlist ? ` · ${selectedWatchlist.name}` : ''}`}
        onClose={() => setShowAddModal(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <input
            type="text"
            value={addSearch}
            onChange={(e) => setAddSearch(e.target.value)}
            placeholder={t("watchlist.searchPh")}
            style={dialogInput}
            autoFocus
          />
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {[{ key: 'ALL', label: t("watchlist.typeAll") }, ...typeGroups].map(g => {
              const active = addType === g.key;
              return (
                <button
                  key={g.key}
                  onClick={() => setAddType(g.key)}
                  style={{
                    padding: '5px 12px', borderRadius: 999, fontSize: 12, fontWeight: 600, cursor: 'pointer',
                    border: active ? '1px solid var(--accent-solid)' : '1px solid var(--border)',
                    background: active ? 'var(--accent)' : 'var(--bg-card)',
                    color: active ? 'var(--accent-solid)' : 'var(--text-muted)',
                  }}
                >
                  {g.label}
                </button>
              );
            })}
          </div>
          <div style={{
            maxHeight: '50vh', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 6,
            // Promote to its own compositor layer so scrolling stays smooth inside
            // the modal's backdrop-filter subtree (which otherwise forces slow
            // main-thread scrolling), and isolate paint to this container.
            transform: 'translateZ(0)', willChange: 'transform', contain: 'content',
            overscrollBehavior: 'contain', WebkitOverflowScrolling: 'touch',
          }}>
            {addCandidates.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)', fontSize: 14 }}>{t("watchlist.noResults")}</div>
            ) : (
              addCandidates.map(inst => {
                const isAdded = addedSymbols.has(inst.symbol);
                return (
                  <div
                    key={inst.symbol}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 10, padding: '8px 10px',
                      borderRadius: 8, border: '1px solid var(--border-soft)', background: 'var(--input-bg)',
                      contain: 'layout paint',
                    }}
                  >
                    <span style={{ fontWeight: 700, color: 'var(--text-primary)', minWidth: 90 }}>{inst.symbol}</span>
                    <span style={{ flex: 1, color: 'var(--text-muted)', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{inst.name ?? '-'}</span>
                    <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', border: '1px solid var(--border)', borderRadius: 6, padding: '2px 8px' }}>{typeLabel(inst.type)}</span>
                    <button
                      onClick={() => isAdded ? handleRemoveFromWatchlist(inst.symbol) : handleAddToWatchlist(inst.symbol)}
                      style={{
                        padding: '6px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer', minWidth: 92,
                        border: isAdded ? '1px solid var(--accent-solid)' : 'none',
                        background: isAdded ? 'transparent' : 'var(--accent-solid)',
                        color: isAdded ? 'var(--accent-solid)' : '#fff',
                      }}
                    >
                      {isAdded ? <><IconCheck size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />{t("watchlist.added")}</> : `+ ${t("watchlist.add")}`}
                    </button>
                  </div>
                );
              })
            )}
          </div>
        </div>
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
            <p style={{ fontSize: '14px', color: 'var(--text-muted)', marginBottom: '18px' }}>{t("watchlist.emptyListSub")}</p>
            <button
              onClick={openAddModal}
              style={{
                padding: '10px 24px', border: 'none', borderRadius: '8px',
                background: 'var(--accent-solid)', color: '#fff', cursor: 'pointer', fontWeight: 'bold'
              }}
            >
              + {t("watchlist.addInstrument")}
            </button>
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
