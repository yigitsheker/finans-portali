import { useState, useEffect } from 'react';
import { watchlistApi } from '../api/watchlistApi';
import { getMarketSummary } from '../api/portfolioApi';
import FinexStyleMarket from './FinexStyleMarket';

const WatchlistManager = ({ keycloak }) => {
  const [watchlists, setWatchlists] = useState([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState(null);
  const [instruments, setInstruments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newWatchlistName, setNewWatchlistName] = useState('');
  const [showRenameModal, setShowRenameModal] = useState(false);
  const [renameWatchlistName, setRenameWatchlistName] = useState('');

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
      alert('Liste oluşturulamadı');
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
      alert('Liste adı değiştirilemedi');
    }
  };

  const handleDeleteWatchlist = async (id) => {
    if (!confirm('Bu listeyi silmek istediğinizden emin misiniz?')) return;

    try {
      await watchlistApi.deleteWatchlist(keycloak, id);
      const newWatchlists = watchlists.filter(w => w.id !== id);
      setWatchlists(newWatchlists);
      if (selectedWatchlist?.id === id) {
        setSelectedWatchlist(newWatchlists[0] || null);
      }
    } catch (error) {
      console.error('Failed to delete watchlist:', error);
      alert('Liste silinemedi');
    }
  };

  const handleAddToWatchlist = async (symbol) => {
    if (!selectedWatchlist) {
      alert('Lütfen önce bir liste seçin');
      return;
    }

    try {
      await watchlistApi.addToWatchlist(keycloak, {
        watchlistId: selectedWatchlist.id,
        symbol
      });

      // Reload the selected watchlist
      const updated = await watchlistApi.getWatchlist(keycloak, selectedWatchlist.id);
      setWatchlists(watchlists.map(w => w.id === updated.id ? updated : w));
      setSelectedWatchlist(updated);
    } catch (error) {
      console.error('Failed to add to watchlist:', error);
      alert('Hisse listeye eklenemedi');
    }
  };

  const handleRemoveFromWatchlist = async (symbol) => {
    if (!selectedWatchlist) return;

    try {
      await watchlistApi.removeFromWatchlist(keycloak, selectedWatchlist.id, symbol);

      // Reload the selected watchlist
      const updated = await watchlistApi.getWatchlist(keycloak, selectedWatchlist.id);
      setWatchlists(watchlists.map(w => w.id === updated.id ? updated : w));
      setSelectedWatchlist(updated);
    } catch (error) {
      console.error('Failed to remove from watchlist:', error);
      alert('Hisse listeden çıkarılamadı');
    }
  };

  const filteredInstruments = selectedWatchlist
    ? instruments.filter(inst => selectedWatchlist.symbols.includes(inst.symbol))
    : [];

  if (loading) {
    return <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>Yükleniyor...</div>;
  }

  return (
    <div style={{ padding: '20px' }}>
      {/* Watchlist Tabs */}
      <div style={{
        display: 'flex',
        gap: '10px',
        marginBottom: '20px',
        flexWrap: 'wrap',
        alignItems: 'center'
      }}>
        {watchlists.map(watchlist => (
          <div key={watchlist.id} style={{ position: 'relative' }}>
            <button
              onClick={() => setSelectedWatchlist(watchlist)}
              style={{
                padding: '10px 40px 10px 20px',
                border: selectedWatchlist?.id === watchlist.id
                  ? '2px solid var(--accent-solid)'
                  : '1px solid var(--border)',
                borderRadius: '8px',
                background: selectedWatchlist?.id === watchlist.id
                  ? 'var(--accent)'
                  : 'var(--bg-card)',
                cursor: 'pointer',
                fontWeight: selectedWatchlist?.id === watchlist.id ? 'bold' : 'normal',
                color: selectedWatchlist?.id === watchlist.id ? 'var(--accent-solid)' : 'var(--text-primary)',
                transition: 'all 0.2s'
              }}
            >
              {watchlist.name} ({watchlist.symbols.length})
            </button>
            {selectedWatchlist?.id === watchlist.id && (
              <div style={{
                position: 'absolute',
                right: '5px',
                top: '50%',
                transform: 'translateY(-50%)',
                display: 'flex',
                gap: '5px'
              }}>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setRenameWatchlistName(watchlist.name);
                    setShowRenameModal(true);
                  }}
                  style={{
                    padding: '2px 6px',
                    border: 'none',
                    background: 'transparent',
                    cursor: 'pointer',
                    fontSize: '14px',
                    opacity: 0.7
                  }}
                  title="Yeniden Adlandır"
                >
                  ✏️
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteWatchlist(watchlist.id);
                  }}
                  style={{
                    padding: '2px 6px',
                    border: 'none',
                    background: 'transparent',
                    cursor: 'pointer',
                    fontSize: '14px',
                    color: 'var(--red)',
                    opacity: 0.7
                  }}
                  title="Sil"
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
            padding: '10px 20px',
            border: '2px dashed var(--accent-solid)',
            borderRadius: '8px',
            background: 'var(--bg-card)',
            cursor: 'pointer',
            color: 'var(--accent-solid)',
            fontWeight: 'bold',
            transition: 'all 0.2s'
          }}
        >
          + Yeni Liste
        </button>
      </div>

      {/* Create Watchlist Modal */}
      {showCreateModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0,0,0,0.7)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            background: 'var(--bg-panel)',
            padding: '30px',
            borderRadius: '12px',
            minWidth: '400px',
            border: '1px solid var(--border)'
          }}>
            <h3 style={{ marginTop: 0, color: 'var(--text-primary)' }}>Yeni Liste Oluştur</h3>
            <input
              type="text"
              value={newWatchlistName}
              onChange={(e) => setNewWatchlistName(e.target.value)}
              placeholder="Liste adı"
              style={{
                width: '100%',
                padding: '10px',
                border: '1px solid var(--input-border)',
                borderRadius: '6px',
                marginBottom: '20px',
                fontSize: '14px',
                background: 'var(--input-bg)',
                color: 'var(--text-primary)'
              }}
              onKeyPress={(e) => e.key === 'Enter' && handleCreateWatchlist()}
              autoFocus
            />
            <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
              <button
                onClick={() => {
                  setShowCreateModal(false);
                  setNewWatchlistName('');
                }}
                style={{
                  padding: '10px 20px',
                  border: '1px solid var(--border)',
                  borderRadius: '6px',
                  background: 'var(--bg-card)',
                  cursor: 'pointer',
                  color: 'var(--text-primary)'
                }}
              >
                İptal
              </button>
              <button
                onClick={handleCreateWatchlist}
                style={{
                  padding: '10px 20px',
                  border: 'none',
                  borderRadius: '6px',
                  background: 'var(--accent-solid)',
                  color: 'white',
                  cursor: 'pointer',
                  fontWeight: 'bold'
                }}
              >
                Oluştur
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rename Watchlist Modal */}
      {showRenameModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0,0,0,0.7)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            background: 'var(--bg-panel)',
            padding: '30px',
            borderRadius: '12px',
            minWidth: '400px',
            border: '1px solid var(--border)'
          }}>
            <h3 style={{ marginTop: 0, color: 'var(--text-primary)' }}>Liste Adını Değiştir</h3>
            <input
              type="text"
              value={renameWatchlistName}
              onChange={(e) => setRenameWatchlistName(e.target.value)}
              placeholder="Yeni liste adı"
              style={{
                width: '100%',
                padding: '10px',
                border: '1px solid var(--input-border)',
                borderRadius: '6px',
                marginBottom: '20px',
                fontSize: '14px',
                background: 'var(--input-bg)',
                color: 'var(--text-primary)'
              }}
              onKeyPress={(e) => e.key === 'Enter' && handleRenameWatchlist()}
              autoFocus
            />
            <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
              <button
                onClick={() => {
                  setShowRenameModal(false);
                  setRenameWatchlistName('');
                }}
                style={{
                  padding: '10px 20px',
                  border: '1px solid var(--border)',
                  borderRadius: '6px',
                  background: 'var(--bg-card)',
                  cursor: 'pointer',
                  color: 'var(--text-primary)'
                }}
              >
                İptal
              </button>
              <button
                onClick={handleRenameWatchlist}
                style={{
                  padding: '10px 20px',
                  border: 'none',
                  borderRadius: '6px',
                  background: 'var(--accent-solid)',
                  color: 'white',
                  cursor: 'pointer',
                  fontWeight: 'bold'
                }}
              >
                Kaydet
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Watchlist Content */}
      {selectedWatchlist ? (
        filteredInstruments.length > 0 ? (
          <FinexStyleMarket
            instruments={filteredInstruments}
            onAddToWatchlist={handleAddToWatchlist}
            onRemoveFromWatchlist={handleRemoveFromWatchlist}
            watchlistSymbols={selectedWatchlist.symbols}
          />
        ) : (
          <div style={{
            padding: '40px',
            textAlign: 'center',
            background: 'var(--bg-card)',
            borderRadius: '12px',
            border: '2px dashed var(--border)'
          }}>
            <p style={{ fontSize: '18px', color: 'var(--text-muted)', marginBottom: '10px' }}>
              Bu liste henüz boş
            </p>
            <p style={{ fontSize: '14px', color: 'var(--text-muted)' }}>
              Hisse Senetleri sekmesinden hisse ekleyebilirsiniz
            </p>
          </div>
        )
      ) : (
        <div style={{
          padding: '40px',
          textAlign: 'center',
          background: 'var(--bg-card)',
          borderRadius: '12px',
          border: '1px solid var(--border)'
        }}>
          <p style={{ fontSize: '18px', color: 'var(--text-muted)' }}>
            Bir liste seçin veya yeni liste oluşturun
          </p>
        </div>
      )}
    </div>
  );
};

export default WatchlistManager;
