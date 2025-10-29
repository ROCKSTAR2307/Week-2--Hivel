const success = (message, data = null) => {
  return {
    success: true,
    message,
    data
  };
};

const error = (message, errors = []) => {
  return {
    success: false,
    message,
    errors
  };
};

module.exports = { success, error };
